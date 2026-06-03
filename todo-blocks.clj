


























 (case> CreateUser :> {:keys [*name]})
  (local-select> (view contains? *username) $$profiles :> *exists?)
  (<<if *exists?
    (ack-return> {:error "User already exists"})
   (else>)
    (System/currentTimeMillis :> *created-at-millis)
    (local-transform> [(keypath *username)
                       (termval {:name *name
                                 :created-at-millis *created-at-millis})]
                      $$profiles))

 (case> EditProfileField :> {:keys [*key *value]})
  (local-transform> [(must *username) (keypath *key) (termval *value)] $$profiles)

 (case> CreateList :> {:keys [*username *name]})
  (System/currentTimeMillis :> *created-at-millis)
  (local-transform>
   [(keypath *list-id) nil?
    (termval {:name *name :created-at-millis *created-at-millis :owners #{*username}})]
   $$lists)
  (|hash *username)
  (local-transform> [(keypath *username) :lists NONE-ELEM (termval *list-id)] $$profiles)

 (case> ShareList :> {:keys [*list-id *to-username]})
  ;; filter that this user is still an owner
  (local-select> [(keypath *username) :lists (set-elem *list-id)] $$profiles)
  (|hash *list-id)
  ;; filter that list still exists
  (local-select> (must *list-id) $$lists)
  (local-transform> [(keypath *list-id) :owners NONE-ELEM (termval *to-username)] $$lists)
  (|hash *to-username)
  (local-transform> [(keypath *to-username) :lists NONE-ELEM (termval *list-id)] $$profiles)

 (case> AddTodo :> {:keys [*todo-id *content]})
  (->TodoItem *todo-id *content false :> *todo)
  (local-transform> [(must *list-id) :items AFTER-ELEM (termval *todo)] $$lists)

 (case> EditTodo :> {:keys [*todo-id *key *value]})
  (local-transform> [(must *list-id)
                     :items
                     ALL
                     (selected? :todo-id (pred= *todo-id))
                     (keypath *key)
                     (termval *value)]
                    $$lists)

 (case> MoveTodo :> {:keys [*todo-id *to-index]})
  (local-select> [(must *list-id) :items (view count)] $$lists :> *size)
  (filter> (< *to-index *size))
  (local-transform> [(must *list-id)
                     :items
                     INDEXED-VALS
                     (selected? LAST :todo-id (pred= *todo-id))
                     FIRST
                     (termval *to-index)]
                    $$lists)

 (case> DeleteTodo :> {:keys [*todo-id]})
  (local-transform> [(must *list-id)
                     :items
                     ALL
                     (selected? :todo-id (pred= *todo-id))
                     NONE>]
                    $$lists)

 (case> RemoveList :> {:keys [*username]})
  (local-transform> [(keypath *list-id) :owners (set-elem *username) NONE>] $$lists)
  (local-select> [(keypath *list-id) :owners (view count)] $$lists :> *num-owners)
  (<<if (zero? *num-owners)
    (local-transform> [(keypath *list-id) NONE>] $$lists))
  (|hash *username)
  (local-transform> [(keypath *username) :lists (set-elem *list-id) NONE>] $$profiles)








(defn current-minute-bucket
  []
  (-> (System/currentTimeMillis)
      (/ 1000)
      (/ 60)
      long))

(def +telemetry-combiner
  (combiner
   (fn [m1 m2]
     (merge-with
      (fn [m3 m4]
        (merge-with + m3 m4))
      m1
      m2))
   :init-fn
   (fn [] {})))

 (source> *list-depot :> %mb)
  (current-minute-bucket :> *bucket)
  (%mb :> *data)
  (class *data :> *class)
  (|global)
  (+compound $$list-ops-telemetry {*bucket {*class (aggs/+count)}})

  ; (current-minute-bucket :> *bucket)
  ; (<<batch
  ;   (%mb :> *data)
  ;   (class *data :> *class)
  ;   (identity {*bucket {*class 1}} :> *m)
  ;   (|global)
  ;   (+telemetry-combiner $$list-ops-telemetry *m))
