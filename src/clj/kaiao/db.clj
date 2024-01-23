(ns kaiao.db
  (:require
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]
   [next.jdbc.quoted :as quoted]
   [next.jdbc.result-set :as rs]
   [kaiao.pg] ; load to register protocol impls
   [kaiao.system :refer [*db*]]
   [clojure.string :as str]
   [camel-snake-kebab.core :as csk]))

(def +rs-opts+
  {:builder-fn rs/as-unqualified-kebab-maps})

(defn- fnil-created-at
  [m]
  (or (:created-at m) (java.time.Instant/now)))

(def ^:private key->extractor-fn
  {:created-at fnil-created-at})

(defn- row-extractor-fn
  [columns]
  (apply juxt (mapv #(key->extractor-fn % %) columns)))

(defn placeholder-list
  [n]
  (str "(" (str/join ", " (repeat n "?")) ")"))



(defn columns-list
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
    (throw (ex-info ":name is required" project)))
  (let [project (merge {:id (random-uuid)} project)]
    (sql/insert! *db* :project project)
    (:id project)))

;;----------------------------------------------------------------------
;; User
;;----------------------------------------------------------------------
(def +user-columns+
  [:project-id :user-id :email :first-name :last-name :name :org-id :org-name :tags :created-at])

(def user->row-vec (row-extractor-fn +user-columns+))

(defn get-user
  [project-id user-id]
  (first (sql/find-by-keys
          *db*
          (quoted/postgres "user")
          {:project_id project-id
           :user_id user-id}
          +rs-opts+)))

(defn put-users!
  [users]
  (jdbc/execute-batch!
   *db*
   (str "insert into " (quoted/postgres "user")
        (columns-list +user-columns+)
        " values "
        (placeholder-list (count +user-columns+))
        " on conflict (project_id, user_id) "
        " do update set email = excluded.email "
        " , first_name = excluded.first_name "
        " , last_name = excluded.last_name "
        " , name = excluded.name "
        " , org_id = excluded.org_id "
        " , org_name = excluded.org_name "
        " , tags = excluded.tags "
        " , updated_at = current_timestamp")
   (mapv user->row-vec users)
   {}))


;;----------------------------------------------------------------------
;; Session
;;----------------------------------------------------------------------
(def +session-columns+
  [:id :project-id :user-id :project-version-id :hostname :browser :os :device :screen :language
   :ip-address :country :city :subdivision-1 :subdivision-2 :created-at])

(def session->row-vec (row-extractor-fn +session-columns+))


(defn get-session
  [id]
  (sql/get-by-id *db* "session" id :id +rs-opts+))

(defn insert-sessions!
  [sessions]
  (jdbc/execute-batch!
   *db*
   (str "insert into " (quoted/postgres "session")
        (columns-list +session-columns+)
        " values "
        (placeholder-list (count +session-columns+)))
   (mapv session->row-vec sessions)
   {}))


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
(def +event-columns+
  [:id :project-id :session-id :name :url-path :url-query :referrer-path :referrer-query :referrer-domain :page-title :created-at])

(def event->row-vec (row-extractor-fn +event-columns+))

(defn get-event
  [id]
  (sql/get-by-id *db* "event" id :id +rs-opts+))

(defn insert-events!
  [events]
  (jdbc/execute-batch!
   *db*
   (str "insert into " (quoted/postgres "event")
        (columns-list +event-columns+)
        " values "
        (placeholder-list (count +event-columns+)))
   (mapv event->row-vec events)
   {}))


;;----------------------------------------------------------------------
;; Event Data
;;----------------------------------------------------------------------
(def +event-data-columns+
  [:event-id :key :string-value :int-value :decimal-value :timestamp-value :json-value :created-at])

(def event-data->row-vec (row-extractor-fn +event-data-columns+))


(defn get-event-data
  [event-id key-name]
  (first (sql/find-by-keys
          *db*
          "event_data"
          {"event_id" event-id
           "key" key-name}
          +rs-opts+)))


(defn insert-event-datas!
  [event-datas]
  (jdbc/execute-batch!
   *db*
   (str "insert into " (quoted/postgres "event_data")
        (columns-list +event-data-columns+)
        " values "
        (placeholder-list (count +event-data-columns+)))
   (mapv event-data->row-vec event-datas)
   {}))


;;----------------------------------------------------------------------
;; Session Data
;;----------------------------------------------------------------------
(def +session-data-columns+
  [:session-id :key :string-value :int-value :decimal-value :timestamp-value :json-value :created-at])

(def session-data->row-vec (row-extractor-fn +session-data-columns+))

(defn get-session-data
  [session-id key-name]
  (first (sql/find-by-keys
          *db*
          "session_data"
          {"session_id" session-id
           "key" key-name}
          +rs-opts+)))

(defn insert-session-datas!
  [session-datas]
  (jdbc/execute-batch!
   *db*
   (str "insert into " (quoted/postgres "session_data")
        (columns-list +session-data-columns+)
        " values "
        (placeholder-list (count +session-data-columns+)))
   (mapv session-data->row-vec session-datas)
   {}))
