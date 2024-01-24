(ns dev
  (:require [clojure.repl]
            [next.jdbc :as jdbc]
            [next.jdbc.quoted :as quoted]
            [kaiao.main :as main]
            [kaiao.flyway :as flyway]
            [kaiao.routes :as routes]
            [kaiao.system :refer [*db*]]))


(def +jdbc-url+ "jdbc:postgresql://localhost:5400/kaiao-db?user=foo&password=bar")

(defn enable-dev-hacks!
  []
  (alter-var-root #'routes/https? (constantly true)))

(defn start!
  []
  (main/start-services! {:kaiao/jdbc-url +jdbc-url+}))

(defn restart!
  []
  (main/stop-services!)
  (start!))


(defn re-init-db!
  []
  (doseq [tbl ["event" "event_data" "project" "session" "session_data" "user" "flyway_schema_history"]]
    (jdbc/execute-one! *db* [ (str "drop table " (quoted/postgres tbl))]))
  (flyway/migrate!))

(comment
  (restart!)

  (re-init-db!))
