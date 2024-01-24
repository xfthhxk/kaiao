(ns kaiao.ingest
  (:require
   [kaiao.db :as db]
   [next.jdbc :as jdbc]
   [kaiao.system :refer [*db*]]
   [kaiao.util :as util]))

(defn- ->data-key
  [x]
  (cond
    (string? x) :string-value
    (symbol? x) :string-value
    (keyword? x) :string-value
    (int? x) :int-value
    (number? x) :decimal-value
    (inst? x) :timestamp-value
    (coll? x) :json-value
    :else :string-value))

(defn- data-rows
  "`m` is a map. Returns a seq of maps"
  [parent-key parent-id created-at m]
  (loop [ans (transient [])
         [[k v :as kv] & more] m]
    (if-not kv
      (persistent! ans)
      (recur (conj! ans {parent-key parent-id
                         :key k
                         (->data-key v) v
                         :created-at created-at})
             more))))

(defn create-session!
  [{:keys [kaiao/remote-ip] :as req}]
  (let [session (:body-params req)
        created-at (or (:created-at session) (util/now))
        session-data (data-rows :session-id (:id session) created-at (:data session))]
    (jdbc/with-transaction [txn *db*]
      (db/insert-sessions! txn [(assoc session :ip-address remote-ip)])
      (db/insert-session-data! txn session-data))))

(defn create-event!
  [req]
  (let [event (:body-params req)
        created-at (or (:created-at event) (util/now))
        event-data (data-rows :event-id (:id event) created-at (:data event))]
    (jdbc/with-transaction [txn *db*]
      (db/insert-events! txn [event])
      (db/insert-event-data! txn event-data))))

(defn identify-session!
  [req]
  (let [{:keys [session-id user]} (:body-params req)]
    (db/put-users! [user])
    (db/identify-session! session-id (:user-id user))))


(defn track!
  [req]
  (let [track (get-in req [:body-params :kaiao/track])
        f (case track
            :kaiao.track/session create-session!
            :kaiao.track/event create-event!
            :kaiao.track/identify identify-session!
            (constantly :kaiao/invalid-request))]
    (f req)))
