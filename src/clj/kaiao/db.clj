(ns kaiao.db
  (:require
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]
   [next.jdbc.quoted :as quoted]
   [next.jdbc.result-set :as rs]
   [kaiao.domain :as domain]
   [kaiao.pg] ; load to register protocol impls
   [kaiao.system :refer [*db*]]
   [kaiao.util :as util]
   [clojure.string :as str]
   [camel-snake-kebab.core :as csk]))

(def +rs-opts+
  {:builder-fn rs/as-unqualified-kebab-maps})

(defn- fnil-created-at
  [m]
  (or (:created-at m) (java.time.Instant/now)))

(defn- ensure-uuid-fn
  [k]
  (fn [m]
    (when-let [v (get m k)]
      (cond
        (uuid? v) v
        (string? v) (java.util.UUID/fromString v)
        :else (throw (ex-info "Invalid uuid" {:v v}))))))

(def ^:private key->extractor-fn
  {:created-at fnil-created-at
   :project-id (ensure-uuid-fn :project-id)
   :session-id (ensure-uuid-fn :session-id)
   :event-id (ensure-uuid-fn :event-id)
   :id (ensure-uuid-fn :id)})

(defn- row-extractor-fn
  [columns]
  (apply juxt (mapv #(key->extractor-fn % %) columns)))

(defn- placeholder-list
  [n]
  (str "(" (str/join ", " (repeat n "?")) ")"))



(defn- columns-list
  [columns]
  (str "("
       (->> columns
            (map csk/->snake_case_string)
            (map quoted/postgres)
            (str/join ", "))
       ")"))

;;----------------------------------------------------------------------
;; Project
;;----------------------------------------------------------------------
(defn get-project
  [id]
  (sql/get-by-id *db* "project" id :id +rs-opts+))

(defn create-project!
  "Creates a project and returns the id"
  [project]
  (when (str/blank? (:name project))
    (throw (ex-info "project name is required" project)))
  (let [project (merge {:id (random-uuid)} project)]
    (sql/insert! *db* :project project)
    (:id project)))

;;----------------------------------------------------------------------
;; User
;;----------------------------------------------------------------------
(def ^:private user-values (row-extractor-fn domain/+allowed-user-keys+))

(defn get-user
  [project-id user-id]
  (first (sql/find-by-keys
          *db*
          (quoted/postgres "user")
          {:project_id project-id
           :user_id user-id}
          +rs-opts+)))

(defn put-users!
  [xs]
  (jdbc/execute-batch!
   *db*
   (str "insert into " (quoted/postgres "user")
        (columns-list domain/+allowed-user-keys+)
        " values "
        (placeholder-list (count domain/+allowed-user-keys+))
        " on conflict (project_id, user_id) "
        " do update set email = excluded.email "
        " , first_name = excluded.first_name "
        " , last_name = excluded.last_name "
        " , name = excluded.name "
        " , org_id = excluded.org_id "
        " , org_name = excluded.org_name "
        " , tags = excluded.tags "
        " , updated_at = current_timestamp")
   (mapv user-values xs)
   {}))


;;----------------------------------------------------------------------
;; Session
;;----------------------------------------------------------------------
(def ^:private session-values (row-extractor-fn domain/+allowed-session-keys+))

(defn get-session
  [id]
  (util/remove-nils (sql/get-by-id *db* "session" id :id +rs-opts+)))

(defn insert-sessions!
  ([xs] (insert-sessions! *db* xs))
  ([db xs]
   (jdbc/execute-batch!
    db
    (str "insert into " (quoted/postgres "session")
         (columns-list domain/+allowed-session-keys+)
         " values "
         (placeholder-list (count domain/+allowed-session-keys+)))
    (mapv session-values xs)
    {})))


(defn identify-session!
  [session-id user-id]
  (jdbc/execute-batch!
   *db*
   (str "update " (quoted/postgres "session")
        "set user_id = ? "
        "where id = ?")
   [[user-id session-id]]
   {}))


;;----------------------------------------------------------------------
;; Event
;;----------------------------------------------------------------------
(def ^:private event-values (row-extractor-fn domain/+allowed-event-keys+))

(defn get-event
  [id]
  (util/remove-nils (sql/get-by-id *db* "event" id :id +rs-opts+)))

(defn insert-events!
  ([xs] (insert-events! *db* xs))
  ([db xs]
   (jdbc/execute-batch!
    db
    (str "insert into " (quoted/postgres "event")
         (columns-list domain/+allowed-event-keys+)
         " values "
         (placeholder-list (count domain/+allowed-event-keys+)))
    (mapv event-values xs)
    {})))


;;----------------------------------------------------------------------
;; Event Data
;;----------------------------------------------------------------------
(def ^:private event-data-values (row-extractor-fn domain/+allowed-event-data-keys+))

(defn get-event-data
  ([event-id]
   (->> (sql/find-by-keys
         *db*
         "event_data"
         {"event_id" event-id}
         +rs-opts+)
        (map util/remove-nils)))
  ([event-id key-name]
   (some-> (sql/find-by-keys
            *db*
            "event_data"
            {"event_id" event-id
             "key" key-name}
            +rs-opts+)
           first
           util/remove-nils)))


(defn insert-event-data!
  ([xs] (insert-event-data! *db* xs))
  ([db xs]
   (when (seq xs)
     (jdbc/execute-batch!
      db
      (str "insert into " (quoted/postgres "event_data")
           (columns-list domain/+allowed-event-data-keys+)
           " values "
           (placeholder-list (count domain/+allowed-event-data-keys+)))
      (mapv event-data-values xs)
      {}))))


;;----------------------------------------------------------------------
;; Session Data
;;----------------------------------------------------------------------
(def ^:private session-data-values (row-extractor-fn domain/+allowed-session-data-keys+))

(defn get-session-data
  ([session-id]
   (->> (sql/find-by-keys
         *db*
         "session_data"
         {"session_id" session-id}
         +rs-opts+)
        (map util/remove-nils)))
  ([session-id key-name]
   (some-> (sql/find-by-keys
            *db*
            "session_data"
            {"session_id" session-id
             "key" key-name}
            +rs-opts+)
           first
           util/remove-nils)))

(defn insert-session-data!
  ([xs] (insert-session-data! *db* xs))
  ([db xs]
   (when (seq xs)
     (jdbc/execute-batch!
      db
      (str "insert into " (quoted/postgres "session_data")
           (columns-list domain/+allowed-session-data-keys+)
           " values "
           (placeholder-list (count domain/+allowed-session-data-keys+)))
      (mapv session-data-values xs)
      {}))))
