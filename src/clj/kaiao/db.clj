(ns kaiao.db
  (:require
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]
   [next.jdbc.quoted :as quoted]
   [kaiao.pg] ; load to register protocol impls
   [kaiao.system :refer [*db*]]
   [clojure.string :as str]
   [camel-snake-kebab.core :as csk]))


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


(defn create-project!
  "Creates a project and returns the id"
  [project]
  (when (str/blank? (:name project))
    (throw (ex-info ":name is required" project)))
  (let [project (merge {:id (random-uuid)} project)]
    (sql/insert! *db* :project project)
    (:id project)))


(def +user-columns+
  [:project-id :user-id :email :first-name :last-name :name :org-id :org-name :tags :created-at])

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
   (mapv (apply juxt +user-columns+) users)
   {}))


(def +session-columns+
  [:id :project-id :user-id :project-version-id :hostname :browser :os :device :screen :language
   :ip-address :country :city :subdivision-1 :subdivision-2 :created-at])

(defn insert-sessions!
  [sessions]
  (jdbc/execute-batch!
   *db*
   (str "insert into " (quoted/postgres "session")
        (columns-list +session-columns+)
        " values "
        (placeholder-list (count +session-columns+)))
   (mapv (apply juxt +session-columns+) sessions)
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


(def +event-columns+
  [:id :project-id :session-id :name :url-path :url-query :referrer-path :referrer-query :referrer-domain :page-title :screen :created-at])

(defn insert-events!
  [events]
  (jdbc/execute-batch!
   *db*
   (str "insert into " (quoted/postgres "event")
        (columns-list +event-columns+)
        " values "
        (placeholder-list (count +event-columns+)))
   (mapv (apply juxt +event-columns+) events)
   {}))


(def +event-data-columns+
  [:event-id :key :string-value :int-value :decimal-value :timestamp-value :json-value :created-at])

(defn insert-event-datas!
  [event-datas]
  (jdbc/execute-batch!
   *db*
   (str "insert into " (quoted/postgres "event_data")
        (columns-list +event-data-columns+)
        " values "
        (placeholder-list (count +event-data-columns+)))
   (mapv (apply juxt +event-data-columns+) event-datas)
   {}))


(def +session-data-columns+
  [:session-id :key :string-value :int-value :decimal-value :timestamp-value :json-value :created-at])

(defn insert-session-datas!
  [session-datas]
  (jdbc/execute-batch!
   *db*
   (str "insert into " (quoted/postgres "session_data")
        (columns-list +session-data-columns+)
        " values "
        (placeholder-list (count +session-data-columns+)))
   (mapv (apply juxt +session-data-columns+) session-datas)
   {}))


(comment
  (create-project! {:id #uuid "b2c33bd3-32f6-4446-8add-3473192e26d4"
                    :name "kaboom"})

  (put-users! [{:project-id #uuid "b2c33bd3-32f6-4446-8add-3473192e26d4"
                :user-id "a-user-id"
                :first-name "User First"
                :last-name "User Last"
                :email "user@example.com"
                :name ""
                :tags ["hello" "bye"]}])


  (insert-sessions!
   [{:id #uuid "55d26c2c-dac9-43ed-afaa-ed0e4f84d706"
     :project-id #uuid "b2c33bd3-32f6-4446-8add-3473192e26d4"
     :user-id "a-user-id"
     :hostname "my-host"
     :os "linux"
     :browser "chrome"}])


  (insert-events!
   [{:id #uuid "3cc7b1a0-fecc-44ed-ad16-59880b02c503"
     :project-id #uuid "b2c33bd3-32f6-4446-8add-3473192e26d4"
     :session-id #uuid "55d26c2c-dac9-43ed-afaa-ed0e4f84d706"
     :name :user/login
     :page "System Login"
     :url-path "/login"}])

  (insert-event-datas!
   [{:event-id #uuid "4ddf4e0a-2f0f-4c1e-903b-4f612282e3e6"
     :key "boo"
     :string-value "hoo"
     :created-at (java.time.Instant/now)}])

  (insert-session-datas!
   [{:session-id #uuid "55d26c2c-dac9-43ed-afaa-ed0e4f84d706"
     :key "boo"
     :numeric-value 83
     :created-at (java.time.Instant/now)}])



  (insert-sessions!
   [{:id #uuid "0dd4a15c-510e-4e43-84aa-37f65a911dd4"
     :project-id #uuid "b2c33bd3-32f6-4446-8add-3473192e26d4"
     :hostname "my-host"
     :os "linux"
     :browser "chrome"}])

  (identify-session! #uuid "0dd4a15c-510e-4e43-84aa-37f65a911dd4"
                     "identified-user-id")

  )
