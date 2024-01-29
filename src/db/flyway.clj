(ns flyway
  (:import
   (org.flywaydb.core Flyway)))


(set! *warn-on-reflection* true)

(defn- env
  [s]
  (System/getenv s))

(defn- configure
  ^Flyway
  [{:keys [jdbc-url user password clean-disabled?]
    :or {clean-disabled? true
         jdbc-url (env "KAIAO_DB_URL")
         user (env "KAIAO_DB_USER")
         password (env "KAIAO_DB_PASSWORD")}}]
  (.load (doto (Flyway/configure)
           (.cleanDisabled clean-disabled?)
           (.locations ^"[Ljava.lang.String;" (into-array ["filesystem:src/db/postgres/migrations"]))
           (.dataSource jdbc-url user password))))

(defn repair!
  [m]
  (let [flyway (configure m)
        result (.repair flyway)]
    result))


(defn migrate!
  [& {:as opts}]
  (let [flyway (configure opts)
        result (.migrate flyway)
        data {:initial-schema-version (.-initialSchemaVersion result)
              :migrations-executed (.-migrationsExecuted result)
              :schema-name (.-schemaName result)
              :success (.-success result)
              :target-schema-version (.-targetSchemaVersion result)}]
    data))

(defn clean!
  [& {:as opts}]
  (let [flyway (configure (assoc opts :clean-disabled? false))
        result (.clean flyway)]
    {:schemas-cleaned (.schemasCleaned result)
     :schemas-dropped (.schemasDropped result)}))
