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
  )
