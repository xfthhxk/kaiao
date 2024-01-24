(ns kaiao.test
  (:require
   [kaiao.test-db :as test-db]
   [kaiao.system :as system]))


;; only for repl based debugging
(defonce ^:dynamic *db* nil)


(defn ensure-system!
  []
  (when-not (test-db/inited?)
    (test-db/init!)))

(defn setup
  []
  (println "test db started"))


(defn teardown []
  (test-db/halt!)
  (alter-var-root #'*db* (constantly nil))
  (println "test db halted"))


(defn kaocha-pre-hook!
  [config]
  (println "Kaocha pre hook")
  (setup)
  config)


(defn kaocha-post-hook!
  [result]
  (println "Kaocha post hook")
  (teardown)
  result)


(defn with-system
  [f]
  (ensure-system!)
  (binding [system/*db* (test-db/new-db!)]
    (alter-var-root #'*db* (constantly system/*db*))
    (f)))
