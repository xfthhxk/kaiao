(ns kaiao.main
  (:gen-class)
  (:require
   [clojure.java.io :as io]
   [com.brunobonacci.mulog :as mu]
   [s-exp.hirundo :as hirundo]
   [s-exp.hirundo.options :as options]
   [s-exp.hirundo.http.request :as request]
   [s-exp.hirundo.http.response :as response]
   [next.jdbc.connection :as jdbc.connection]
   [kaiao.system :refer [*server* *db*]]
   [kaiao.flyway :as flyway]
   [kaiao.routes :as routes])
  (:import
   (com.zaxxer.hikari HikariDataSource)
   (java.util.jar Manifest)
   (io.helidon.webserver WebServerConfig$Builder)
   (io.helidon.webserver.http ErrorHandler Handler HttpRouting ServerResponse)))

(set! *warn-on-reflection* true)

(defonce stop-publisher-fn nil)


(defn- git-sha
  "Returns the git-sha from the jar's MANIFEST.MF file if one exists else nil"
  []
  (some-> (io/resource "META-INF/MANIFEST.MF")
          io/input-stream
          (Manifest.)
          (.getMainAttributes)
          (.getValue "git-sha")))

(defn final-error-handler
  [_req ^ServerResponse resp ^Throwable t]
  (mu/log :kaiao/unhandled-error :exception t)
  (doto resp
    (.status 500)
    (.send (.getBytes "Unexpected server error"))))


(defn set-ring1-handler! ^WebServerConfig$Builder
  [^WebServerConfig$Builder builder handler _options]
  (doto builder
    (.addRouting
     (-> (HttpRouting/builder)
         (.any ^"[Lio.helidon.webserver.http.Handler;"
               (into-array Handler
                           [(reify Handler
                              (handle [_ server-request server-response]
                                (->> (request/ring-request server-request server-response)
                                     handler
                                     (response/set-response! server-response))))]))
         (.error Throwable (reify ErrorHandler
                             (handle [_this req resp t]
                               (final-error-handler req resp t))))))))

(defmethod options/set-server-option! :http-handler
  [^WebServerConfig$Builder builder _ handler options]
  (mu/log :kaiao/build-http-handler)
  (set-ring1-handler! builder handler options))


(defn start-server!
  [& {:as opts}]
  (when-not *server*
    (let [srv (hirundo/start!
               {:http-handler #'routes/router
                :port (:kaiao/http-port opts 8080)})]
      (alter-var-root #'*server* (constantly srv))
      (mu/log :kaiao/server-started))))


(defn stop-server!
  []
  (some-> *server* hirundo/stop!)
  (alter-var-root #'*server* (constantly nil)))



(defn create-datasource
  [{:keys [kaiao/jdbc-url]}]
  (jdbc.connection/->pool
   HikariDataSource {:jdbcUrl jdbc-url
                     :reWriteBatchedInserts true}))

(defn create-datasource!
  ([]
   (create-datasource! {:kaiao/jdbc-url (System/getenv "KAIAO_DB_URL")}))
  ([opts]
   (let [ds (create-datasource opts)]
     (alter-var-root #'*db* (constantly ds)))))


(defn close-datasource!
  []
  (when *db*
    (.close ^java.io.Closeable *db*)))


(defn start-services!
  [& {:as opts}]
  (let [stop-fn (mu/start-publisher! {:type :console})]
    (alter-var-root #'stop-publisher-fn (constantly stop-fn))
    (create-datasource! opts)
    (flyway/migrate! *db*) ;; TODO make configurable
    (start-server!)))

(defn stop-services!
  []
  (stop-server!)
  (mu/log :kaiao/server-stopped)
  (when stop-publisher-fn
    (stop-publisher-fn)))


(defn -main
  [& _args]
  (-> (Runtime/getRuntime)
      (.addShutdownHook (Thread. ^Runnable stop-services!)))
  (mu/log :kaiao/main :git/sha (git-sha))
  (start-services!))


(comment
  (start-services!)
  (stop-services!)

  )
