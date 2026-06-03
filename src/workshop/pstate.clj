(ns workshop.pstate
  (:use [com.rpl.rama]
        [com.rpl.rama path])
  (:require
    [com.rpl.rama.test :as rtest]))


(defn test-pstate
  []
  (rtest/create-test-pstate {String {String [Object]}}))


(comment
  (use 'com.rpl.rama)
  (use 'com.rpl.rama.path)
  (require '[com.rpl.rama.test :as rtest])
  (require '[workshop.pstate :as pstate] :reload)
  (def p (pstate/test-pstate))

  (rtest/test-pstate-select-one STAY p)


  (rtest/test-pstate-transform [(keypath "a0" "a1") (termval [9 3 6])] p)
  (rtest/test-pstate-transform [(keypath "a0" "b1") (termval [0 8])] p)
  (rtest/test-pstate-transform [(keypath "b0" "c1") (termval ["x" "y"])] p)

  (rtest/test-pstate-select [MAP-VALS MAP-VALS ALL] p)
  (rtest/test-pstate-select [MAP-VALS MAP-VALS ALL number? even?] p)
  (rtest/test-pstate-select [MAP-VALS MAP-KEYS] p)
  (rtest/test-pstate-select [MAP-VALS MAP-VALS (selected? (view count) (pred> 2))] p)

  (rtest/test-pstate-transform [(keypath "a0" "a1") (nthpath 1) NONE>] p)
  (rtest/test-pstate-transform [(keypath "b0" "c1") AFTER-ELEM (termval "z")] p)
  (rtest/test-pstate-transform [MAP-VALS MAP-VALS AFTER-ELEM (termval "!")] p)
  (rtest/test-pstate-transform [(keypath "a0" "b1") NONE>] p)
  )
