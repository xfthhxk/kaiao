(ns kaiao.test
  (:require
   [kaiao.test-db :as test-db]
   [kaiao.system :refer [*db*]]))


;; only for repl based debugging
(defonce ^:dynamic *test-db* nil)


(defn ensure-system!
  []
  (when-not (test-db/inited?)
    (test-db/init!)))

(defn setup
  []
  (println "test db started"))


(defn teardown []
  (test-db/halt!)
  (println "test db halted"))


(defn kaocha-pre-hook!
  [config]
  (println "Koacha pre hook")
  (setup)
  config)


(defn kaocha-post-hook!
  [result]
  (println "Koacha post hook")
  (teardown)
  result)


(defn with-system
  [f]
  (ensure-system!)
  (binding [*db* (test-db/new-db!)]
    (alter-var-root #'*test-db* (constantly *db*))
    (f)))
