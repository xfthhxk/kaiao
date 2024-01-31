(ns kaiao.test-db
  "Example of using testcontainers and launching a postgres container with flyway migrations.
  NB. databases are created but not destroyed which can be handy for debugging tests.
  * Connecting to template1 from psql for example will cause create database to fail
  * Based on ideas from https://github.com/opentable/otj-pg-embedded"
  (:require [clj-test-containers.core :as tc]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [flyway :as flyway]
            [next.jdbc.connection :as jdbc.connection])
  (:import (com.zaxxer.hikari HikariDataSource)))



(defonce ^:dynamic *pg-container* nil)


(def ^:const +pg-user+ "foo")
(def ^:const +pg-password+ "bar")
(def ^:const +pg-port+ 5432)
(def ^:private ^:dynamic *future* nil)
(def ^:private ^:dynamic *queue* (java.util.concurrent.SynchronousQueue.))

(defonce +db-spec+
  ;; :host and :port are merged in after the container is started
  ;; :dbname need to be determined by the user
  {:dbtype "postgresql"
   :dbname "template1"
   :user +pg-user+
   :username +pg-user+
   :password +pg-password+
   :reWriteBatchedInserts true})

(defn halt!
  []
  (some-> *future* future-cancel)
  (some-> *pg-container* tc/stop!)
  (alter-var-root #'*pg-container* (constantly nil))
  :halted)


(defn- launch-container
  []
  (-> {:image-name "postgres:13.6"
       :exposed-ports [+pg-port+]
       :env-vars {"POSTGRES_USER" +pg-user+
                  "POSTGRES_PASSWORD" +pg-password+
                  "POSTGRES_DB" "postgres"}}
      tc/create
      tc/start!))

(defn- produce-db!
  []
  (let [db-spec (assoc +db-spec+ :dbname (str (gensym "pg_db_")))
        ddl (format "create database \"%s\" owner \"%s\" encoding = 'utf8'"
                    (:dbname db-spec)
                    +pg-user+)]
    (jdbc/execute-one! +db-spec+ [ddl] {:transaction? false})
    db-spec))


(defn start-db-producer!
  []
  (let [f (future
            (while (not (Thread/interrupted))
              (try
                (.put *queue* (produce-db!))
                (catch java.sql.SQLException ex
                  (.put *queue* {:ex ex})))))]
    (alter-var-root #'*future* (constantly f))))

(defn init!
  []
  (let [c (launch-container)]
    (alter-var-root #'*pg-container* (constantly c))
    ;; fill out the spec
    (alter-var-root #'+db-spec+
                    assoc
                    :host (:host *pg-container*)
                    :port (get (:mapped-ports *pg-container*) +pg-port+))
    (flyway/migrate! {:jdbc-url (str "jdbc:"
                                     (:dbtype +db-spec+)
                                     "://"
                                     (:host +db-spec+)
                                     ":"
                                     (:port +db-spec+)
                                     "/"
                                     (:dbname +db-spec+))
                      :user +pg-user+
                      :password +pg-password+})
    (produce-db!)
    (start-db-producer!)))

(defn inited?
  []
  (some? *pg-container*))


(defn new-db!
  []
  (assert (inited?) "test-db not inited!")
  (let [{:keys [ex] :as db} (.take *queue*)]
    (when ex
      (throw (ex-info "db production failed" {} ex)))
    db))


(comment

  ;; TODO:
  ;; - Better error handling
  ;; - Keep only the 100 most recent databases. Drop older ones

  ;; Example usage:

  (init!)

  (def -new-db (new-db!))
  (sql/query -new-db ["select current_database()"])
  (halt!)

  (produce-db!)
  (halt!)

  )
