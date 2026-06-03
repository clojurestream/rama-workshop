(ns workshop.todo
  (:use [com.rpl.rama]
        [com.rpl.rama path])
  (:require
   [com.rpl.rama.aggs :as aggs])
  (:import
   [java.util
    UUID]))


;; for *user-depot
(defrecord CreateUser
  [username name])

(defrecord EditProfileField
  [username key value])

(defrecord ShareList
  [username list-id to-username])

;; for *list-depot
(defrecord CreateList
  [list-id username name])

(defrecord RemoveList
  [list-id username])

(defrecord AddTodo
  [list-id todo-id content])

(defrecord DeleteTodo
  [list-id todo-id])

(defrecord EditTodo
  [list-id todo-id key value])

(defrecord MoveTodo
  [list-id todo-id to-index])

;; for $$lists
(defrecord TodoItem
  [todo-id content complete?])


(defn create-interactive-topology!
  [topologies]
  (let [s (stream-topology topologies "core")]
    (declare-pstate
     s
     $$profiles
     {String
      (fixed-keys-schema
       {:name     String
        :location String
        :created-at-millis Long
        :lists    (set-schema UUID {:subindex? true})
       })})
    (declare-pstate
     s
     $$lists
     {UUID
      (fixed-keys-schema
       {:name   String
        :created-at-millis Long
        :items  [TodoItem]
        :owners (set-schema String {:subindex? true})
       })})

    (<<sources s
     (source> *user-depot :> {:keys [*username] :as *data})
      (<<subsource *data
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

       (case> ShareList :> {:keys [*list-id *to-username]})
        ;; filter that this user is still an owner
        (local-select> [(keypath *username) :lists (set-elem *list-id)] $$profiles)
        (|hash *list-id)
        ;; filter that list still exists
        (local-select> (must *list-id) $$lists)
        (local-transform> [(keypath *list-id) :owners NONE-ELEM (termval *to-username)] $$lists)
        (|hash *to-username)
        (local-transform> [(keypath *to-username) :lists NONE-ELEM (termval *list-id)] $$profiles)
      )

     (source> *list-depot :> {:keys [*list-id] :as *data})
      (<<subsource *data
       (case> CreateList :> {:keys [*username *name]})
        (System/currentTimeMillis :> *created-at-millis)
        (local-transform>
         [(keypath *list-id) nil?
          (termval {:name *name :created-at-millis *created-at-millis :owners #{*username}})]
         $$lists)
        (|hash *username)
        (local-transform> [(keypath *username) :lists NONE-ELEM (termval *list-id)] $$profiles)

       (case> RemoveList :> {:keys [*username]})
        (local-transform> [(keypath *list-id) :owners (set-elem *username) NONE>] $$lists)
        (local-select> [(keypath *list-id) :owners (view count)] $$lists :> *num-owners)
        (<<if (zero? *num-owners)
          (local-transform> [(keypath *list-id) NONE>] $$lists))
        (|hash *username)
        (local-transform> [(keypath *username) :lists (set-elem *list-id) NONE>] $$profiles)

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

       (case> DeleteTodo :> {:keys [*todo-id]})
        (local-transform> [(must *list-id)
                           :items
                           ALL
                           (selected? :todo-id (pred= *todo-id))
                           NONE>]
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
      )
    )))

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

(defn create-analytics-topology!
  [topologies]
  (let [mb (microbatch-topology topologies "analytics")]
    (declare-pstate mb
                    $$list-ops-telemetry
                    {Long ; minute bucket
                     (map-schema
                      Class ; operation type
                      Long ; count
                     )}
                    {:global? true})

    (<<sources mb
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
    )
  ))

(defmodule TodoAppModule
  [setup topologies]
  (declare-depot setup *user-depot (hash-by :username))
  (declare-depot setup *list-depot (hash-by :list-id))

  (create-interactive-topology! topologies)
  (create-analytics-topology! topologies))
