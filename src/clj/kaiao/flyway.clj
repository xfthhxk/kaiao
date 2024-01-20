(ns kaiao.flyway
  (:require
   [com.brunobonacci.mulog :as mu]
   [kaiao.system :refer [*db*]])
  (:import
   (org.flywaydb.core Flyway)))


(set! *warn-on-reflection* true)

(defn- configure
  ^Flyway
  [datasource]
  (.load (doto (Flyway/configure)
           (.cleanDisabled true)
           (.locations ^"[Ljava.lang.String;" (into-array ["classpath:db/postgres/migrations"]))
           (.dataSource datasource))))

(defn repair!
  [m]
  (mu/log :kaiao/start-repair-db!)
  (let [flyway (configure m)
        result (.repair flyway)]
    (mu/log :kaiao/finish-repair-db! :result result)
    result))


(defn migrate!
  ([] (migrate! *db*))
  ([datasource]
   (mu/log :kaiao/start-migrate-db!)
   (let [flyway (configure datasource)
         result (.migrate flyway)
         data {:initial-schema-version (.-initialSchemaVersion result)
               :migrations-executed (.-migrationsExecuted result)
               :schema-name (.-schemaName result)
               :success (.-success result)
               :target-schema-version (.-targetSchemaVersion result)}]
     (mu/log :kaiao/finish-migrate-db! :result data)
     data)))
