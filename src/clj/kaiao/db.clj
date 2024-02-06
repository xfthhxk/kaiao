(ns kaiao.db
  (:require
   [next.jdbc :as jdbc]
   [next.jdbc.date-time :as date-time]
   [next.jdbc.sql :as sql]
   [next.jdbc.quoted :as quoted]
   [next.jdbc.result-set :as rs]
   [next.jdbc.default-options]
   [kaiao.domain :as domain]
   [kaiao.ext :as ext]
   [kaiao.pg] ; load to register protocol impls
   [kaiao.system :refer [*db*]]
   [clojure.string :as str]
   [camel-snake-kebab.core :as csk])
  (:import (java.sql ResultSet)))

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


(defn- as-instant-fn
  [k & {:keys [optional?]}]
  (fn [m]
    (or (some-> (get m k) ext/coerce-instant)
        (when-not optional? (ext/now)))))

(defn- ensure-uuid-fn
  [k]
  (fn [m]
    (or (some-> (get m k) ext/coerce-uuid)
        (throw (ex-info "Invalid uuid" {:ex/tags #{:ex/validation}
                                        :x (get m k)})))))

(def ^:private key->extractor-fn
  {:started-at (as-instant-fn :started-at)
   :ended-at (as-instant-fn :ended-at :optional? true)
   :occurred-at (as-instant-fn :occurred-at)
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
  (sql/get-by-id *db* "projects" (ext/coerce-uuid id) :id +rs-opts+))

(defn create-project!
  "Creates a project and returns the id"
  [project]
  (when (str/blank? (:name project))
    (throw (ex-info "project name is required" {:ex/tags #{:ex/validation}
                                                :project project})))
  (let [project (merge {:id (random-uuid)} project)]
    (sql/insert! *db* "projects" project)
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
          "users"
          {:project_id (ext/coerce-uuid project-id)
           :id user-id}
          +rs-opts+)))

(defn put-users!
  [xs]
  (jdbc/execute-batch!
   *db*
   (str "insert into users "
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
        " , data = excluded.data "
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
  (sql/get-by-id *db* "sessions" (ext/coerce-uuid id) :id +rs-opts+))

(defn insert-sessions!
  ([xs] (insert-sessions! *db* xs))
  ([db xs]
   (jdbc/execute-batch!
    db
    (str "insert into sessions "
         (columns-list domain/+allowed-session-keys+)
         " values "
         (placeholder-list (count domain/+allowed-session-keys+))
         " on conflict (id) do nothing ")
    (mapv session-values xs)
    {})))


(defn identify-session!
  [session-id user-id]
  (sql/update! *db* "sessions" {:user_id user-id} [" id = ?" (ext/coerce-uuid session-id)]))


(defn end-session!
  [session-id ended-at]
  (sql/update! *db* "sessions" {:ended_at (ext/coerce-instant ended-at)} [" id = ?" (ext/coerce-uuid session-id)]))


;;----------------------------------------------------------------------
;; Event
;;----------------------------------------------------------------------
(def ^:private event-values (row-extractor-fn domain/+allowed-event-keys+))

(defn get-event
  [id]
  (sql/get-by-id *db* "events" (ext/coerce-uuid id) :id +rs-opts+))

(defn insert-events!
  ([xs] (insert-events! *db* xs))
  ([db xs]
   (jdbc/execute-batch!
    db
    (str "insert into events "
         (columns-list domain/+allowed-event-keys+)
         " values "
         (placeholder-list (count domain/+allowed-event-keys+))
         " on conflict (id) do nothing ")
    (mapv event-values xs)
    {})))
