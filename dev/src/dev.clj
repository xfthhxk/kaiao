(ns dev
  (:require [clojure.repl]
            [kaiao.main :as main]
            [kaiao.routes :as routes]))

(defn enable-dev-hacks!
  []
  (alter-var-root #'routes/*https-required* (constantly false)))

(defn start!
  []
  (main/start-services! {:kaiao/db-url "jdbc:postgresql://localhost:5400/kaiao-db"
                         :kaiao/db-user "foo"
                         :kaiao/db-password "bar"
                         :kaiao/http-port 9000
                         :kaiao/routes-prefix ""})
  (enable-dev-hacks!))

(defn restart!
  []
  (main/stop-services!)
  (start!))


(comment
  (restart!)
  )
