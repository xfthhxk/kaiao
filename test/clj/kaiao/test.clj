(ns kaiao.test
  (:require
   [kaiao.test-db :as test-db]
   [kaiao.system :as system]
   [kaiao.routes :as routes]
   [kaiao.geo-ip :as geo-ip]
   [charred.api :as charred]
   [com.brunobonacci.mulog :as mu]))


;; only for repl based debugging
(defonce ^:dynamic *db* nil)

(defonce stop-publisher-fn nil)



(defn setup
  []
  (println "Setting up test system")
  (let [stop-fn (mu/start-publisher! {:type :console})]
    (alter-var-root #'stop-publisher-fn (constantly stop-fn))
    (geo-ip/init! "./data/geo-lite2-city.mmdb")
    (test-db/init!)))


(defn teardown []
  (geo-ip/shutdown!)
  (test-db/halt!)
  (alter-var-root #'*db* (constantly nil))
  (when stop-publisher-fn
    (stop-publisher-fn)
    (alter-var-root #'stop-publisher-fn (constantly nil)))
  (println "test db halted"))

(defn ensure-system!
  []
  (when-not (test-db/inited?)
    (setup)))


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


(defn track-request
  [& {:keys [headers body]}]
  (#'routes/router
   {:request-method :post
    :uri "/track"
    :headers (merge {"accept" "application/json"
                     "content-type" "application/json"
                     "x-forwarded-proto" "https"
                     "x-forwarded-for" "127.0.0.1"}
                    headers)
    :body (charred/write-json-str body)}))
