(ns kaiao.db
  (:require
   [next.jdbc :as jdbc]
   [next.jdbc.date-time :as date-time]
   [next.jdbc.sql :as sql]
   [next.jdbc.quoted :as quoted]
   [next.jdbc.result-set :as rs]
   [next.jdbc.default-options]
   [kaiao.domain :as domain]
   [kaiao.pg] ; load to register protocol impls
   [kaiao.system :refer [*db*]]
   [clojure.string :as str]
   [camel-snake-kebab.core :as csk])
  (:import (java.sql ResultSet)
           (java.time Instant)))

(set! *warn-on-reflection* true)

(date-time/read-as-instant) ;; reads sql date and timestamp as instants

;; does not include keys where the value is nil
(defrecord MapResultSetBuilder [^ResultSet rs rsmeta cols]
  rs/RowBuilder
  (->row [_this] (transient {}))
  (column-count [_this] (count cols))
  (with-column [this row i]
    (rs/with-column-value this row (nth cols (dec i))
      (rs/read-column-by-index (.getObject rs ^Integer i) rsmeta i)))
  (with-column-value [_this row col v]
    (cond-> row
      (some? v) (assoc! col v)))
  (row! [_this row]
    (persistent! row))
  rs/ResultSetBuilder
  (->rs [_this] (transient []))
  (with-row [_this mrs row]
    (conj! mrs row))
  (rs! [_this mrs]
    (persistent! mrs)))


(defn- as-maps
  "Given a `ResultSet` and options, return a `RowBuilder` / `ResultSetBuilder`
  that produces bare vectors of hash map rows, with simple, modified keys. "
  [^ResultSet rs opts]
  (let [rsmeta (.getMetaData rs)
        cols   (rs/get-unqualified-modified-column-names
                rsmeta
                (assoc opts :label-fn csk/->kebab-case))]
    (->MapResultSetBuilder rs rsmeta cols)))


(def +rs-opts+
  {:builder-fn as-maps})


(defn- fnil-created-at
  [{:keys [created-at]}]
  (cond
    (int? created-at) (Instant/ofEpochMilli created-at)
    (string? created-at) (Instant/parse created-at)
    :else (Instant/now)))

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
  ([columns] (row-extractor-fn key->extractor-fn columns))
  ([key->fn columns]
   (apply juxt (mapv #(key->fn % %) columns))))

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
(def ^:private user-values
  ;; a User's :id field is a string so have it be the key
  (row-extractor-fn (dissoc key->extractor-fn :id) domain/+allowed-user-keys+))

(defn get-user
  [project-id user-id]
  (first (sql/find-by-keys
          *db*
          (quoted/postgres "user")
          {:project_id project-id
           :id user-id}
          +rs-opts+)))

(defn put-users!
  [xs]
  (jdbc/execute-batch!
   *db*
   (str "insert into " (quoted/postgres "user")
        (columns-list domain/+allowed-user-keys+)
        " values "
        (placeholder-list (count domain/+allowed-user-keys+))
        " on conflict (id, project_id) "
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
  (sql/get-by-id *db* "session" id :id +rs-opts+))

(defn insert-sessions!
  ([xs] (insert-sessions! *db* xs))
  ([db xs]
   (jdbc/execute-batch!
    db
    (str "insert into " (quoted/postgres "session")
         (columns-list domain/+allowed-session-keys+)
         " values "
         (placeholder-list (count domain/+allowed-session-keys+))
         " on conflict (id) do nothing ")
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
  (sql/get-by-id *db* "event" id :id +rs-opts+))

(defn insert-events!
  ([xs] (insert-events! *db* xs))
  ([db xs]
   (jdbc/execute-batch!
    db
    (str "insert into " (quoted/postgres "event")
         (columns-list domain/+allowed-event-keys+)
         " values "
         (placeholder-list (count domain/+allowed-event-keys+))
         " on conflict (id) do nothing ")
    (mapv event-values xs)
    {})))


;;----------------------------------------------------------------------
;; Event Data
;;----------------------------------------------------------------------
(def ^:private event-data-values (row-extractor-fn domain/+allowed-event-data-keys+))

(defn get-event-data
  ([event-id]
   (sql/find-by-keys
    *db*
    "event_data"
    {"event_id" event-id}
    +rs-opts+))
  ([event-id key-name]
   (some-> (sql/find-by-keys
            *db*
            "event_data"
            {"event_id" event-id
             "key" key-name}
            +rs-opts+)
           first)))


(defn insert-event-data!
  ([xs] (insert-event-data! *db* xs))
  ([db xs]
   (when (seq xs)
     (jdbc/execute-batch!
      db
      (str "insert into " (quoted/postgres "event_data")
           (columns-list domain/+allowed-event-data-keys+)
           " values "
           (placeholder-list (count domain/+allowed-event-data-keys+))
           " on conflict (event_id, key) do nothing ")
      (mapv event-data-values xs)
      {}))))


;;----------------------------------------------------------------------
;; Session Data
;;----------------------------------------------------------------------
(def ^:private session-data-values (row-extractor-fn domain/+allowed-session-data-keys+))

(defn get-session-data
  ([session-id]
   (sql/find-by-keys
    *db*
    "session_data"
    {"session_id" session-id}
    +rs-opts+))
  ([session-id key-name]
   (some-> (sql/find-by-keys
            *db*
            "session_data"
            {"session_id" session-id
             "key" key-name}
            +rs-opts+)
           first)))

(defn insert-session-data!
  ([xs] (insert-session-data! *db* xs))
  ([db xs]
   (when (seq xs)
     (jdbc/execute-batch!
      db
      (str "insert into " (quoted/postgres "session_data")
           (columns-list domain/+allowed-session-data-keys+)
           " values "
           (placeholder-list (count domain/+allowed-session-data-keys+))
           " on conflict (session_id, key) do nothing ")
      (mapv session-data-values xs)
      {}))))
