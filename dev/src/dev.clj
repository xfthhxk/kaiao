(ns dev
  (:require [clojure.repl]
            [kaiao.main :as main]
            [kaiao.routes :as routes]))


(def +jdbc-url+ "jdbc:postgresql://localhost:5400/kaiao-db?user=foo&password=bar")

(defn enable-dev-hacks!
  []
  (alter-var-root #'routes/*https-required* (constantly false)))

(defn start!
  []
  (main/start-services! {:kaiao/jdbc-url +jdbc-url+
                         :kaiao/http-port 9000})
  (enable-dev-hacks!))

(defn restart!
  []
  (main/stop-services!)
  (start!))


(comment
  (restart!)
  )
