(ns kaiao.ingest
  (:require
   [kaiao.db :as db]
   [clojure.string :as str]
   [next.jdbc :as jdbc]
   [kaiao.system :refer [*db*]]))

(defn now [] (java.time.Instant/now))

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
  ([parent-key parent-id created-at m]
   (-> (transient [])
       (data-rows parent-key parent-id created-at m)
       persistent!))
  ([ans parent-key parent-id created-at m]
   (loop [ans ans
          [[k v :as kv] & more] m]
     (cond
       (not kv) ans
       (or (not v)
           (and (string? v) (str/blank? v))
           (and (coll? v) (empty? v))) (recur ans more)
       :else (-> ans
                 (conj! {parent-key parent-id
                         :key k
                         (->data-key v) v
                         :created-at created-at})
                 (recur more))))))

(defn create-session!
  [session _metadata]
  (let [created-at (or (:created-at session) (now))
        session-data (data-rows :session-id (:id session) created-at (:data session))]
    (jdbc/with-transaction [txn *db*]
      (db/insert-sessions! txn [session])
      (db/insert-session-data! txn session-data))))

(defn- events->events-data
  [events]
  (let [default-created-at (now)]
    (loop [ans (transient [])
           [e & more] events]
      (if-not e
        (persistent! ans)
        (let [created-at (or (:created-at e) default-created-at)]
          (recur (data-rows ans :event-id (:id e) created-at (:data e))
                 more))))))

(defn create-events!
  [events _metadata]
  (let [event-data (events->events-data events)]
    (jdbc/with-transaction [txn *db*]
      (db/insert-events! txn events)
      (db/insert-event-data! txn event-data))))

(defn identify-session!
  [{:keys [session-id user] :as _data} _metadata]
  (db/put-users! [user])
  (db/identify-session! session-id (:id user)))


(defn track!
  [req]
  (let [{:keys [data metadata]} (:body-params req)
        remote-ip (:kaiao/remote-ip req)
        f (case (-> metadata :op keyword)
            :kaiao.op/session (fn [data metadata]
                                (-> data
                                    (assoc :ip-address remote-ip)
                                    (create-session! metadata)))
            :kaiao.op/events create-events!
            :kaiao.op/identify identify-session!
            (constantly :kaiao/invalid-request))
        res (f data metadata)]
    (when (= :kaiao/invalid-request res)
      {:status 400
       :headers {}
       :body {:error "invalid request op"}})))
