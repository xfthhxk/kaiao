(ns kaiao.main
  (:gen-class)
  (:require
   [clojure.java.io :as io]
   [clojure.edn :as edn]
   [clojure.string :as str]
   [com.brunobonacci.mulog :as mu]
   [s-exp.hirundo :as hirundo]
   [s-exp.hirundo.options :as options]
   [s-exp.hirundo.http.request :as request]
   [s-exp.hirundo.http.response :as response]
   [next.jdbc.connection :as jdbc.connection]
   [kaiao.system :refer [*server* *db*]]
   [kaiao.geo-ip :as geo-ip]
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
                :port (or (:kaiao/http-port opts)
                          (some-> "PORT" System/getenv parse-long int)
                          8080)})]
      (alter-var-root #'*server* (constantly srv))
      (mu/log :kaiao/server-started))))


(defn stop-server!
  []
  (some-> *server* hirundo/stop!)
  (alter-var-root #'*server* (constantly nil)))


(defn create-datasource
  [{:keys [kaiao/db-url kaiao/db-user kaiao/db-password] :as opts}]
  (assert db-url "db-url is required")
  (assert db-user "db-user is required")
  (assert db-password "db-password is required")
  (jdbc.connection/->pool
   HikariDataSource {:jdbcUrl db-url
                     :username db-user
                     :password db-password
                     :reWriteBatchedInserts true}))

(defn create-datasource!
  ([]
   (create-datasource! {}))
  ([opts]
   (let [ds (create-datasource opts)]
     (alter-var-root #'*db* (constantly ds)))))


(defn close-datasource!
  []
  (when *db*
    (.close ^java.io.Closeable *db*)))

(defn url-rewrite-fn
  [prefix]
  (let [n (count prefix)]
    (if (str/blank? prefix)
      identity
      (fn [{:keys [uri] :as req}]
        (if (str/starts-with? uri prefix)
          (update req :uri subs n)
          req)))))

(defn setup-url-rewrite!
  ([] (setup-url-rewrite! {}))
  ([{:keys [kaiao/routes-prefix]}]
   (let [routes-prefix (or routes-prefix
                           (System/getenv "KAIAO_ROUTES_PREFIX"))]
     (mu/log :kaiao/setup-url-rewrite :kaiao/routes-prefix routes-prefix)
     (alter-var-root #'routes/*uri-rewrite-fn* (constantly (url-rewrite-fn routes-prefix))))))


(defn setup-geo-ip!
  [{:keys [kaiao/geo-ip-file]}]
  (if geo-ip-file
    (try
      (geo-ip/init! geo-ip-file)
      (mu/log :kaiao/geo-ip-setup :status :available)
      (catch Throwable t
        (mu/log :kaiao/geo-ip-setup :status :not-available :file geo-ip-file :ex-message (ex-message t))))
    (mu/log :kaiao/geo-ip-setup :status :no-file-available)))

(defn start-services!
  [& {:as opts}]
  (let [stop-fn (mu/start-publisher! {:type :console})]
    (alter-var-root #'stop-publisher-fn (constantly stop-fn))
    (setup-url-rewrite! opts)
    (create-datasource! opts)
    (setup-geo-ip! opts)
    (start-server! opts)))

(defn stop-services!
  []
  (stop-server!)
  (mu/log :kaiao/server-stopped)
  (when stop-publisher-fn
    (stop-publisher-fn)))

(defn env-opts
  []
  {:kaiao/db-url (System/getenv "KAIAO_DB_URL")
   :kaiao/db-user (System/getenv "KAIAO_DB_USER")
   :kaiao/db-password (System/getenv "KAIAO_DB_PASSWORD")
   :kaiao/geo-ip-file (System/getenv "KAIAO_GEO_IP_FILE")})

(defn -main
  [& args]
  (when-not (even? (count args))
    (println "kaiao.main/-main: Even number of args expected")
    (System/exit 1))
  (-> (Runtime/getRuntime)
      (.addShutdownHook (Thread. ^Runnable stop-services!)))
  (let [cli-opts (->> args
                      (map edn/read-string)
                      (apply hash-map))]
    (mu/log :kaiao/main :git/sha (git-sha))
    (start-services! (merge (env-opts) cli-opts))))


(comment
  (start-services!)
  (stop-services!)
  )
