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
  ([parent-key parent-id m]
   (-> (transient [])
       (data-rows parent-key parent-id m)
       persistent!))
  ([ans parent-key parent-id m]
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
                         (->data-key v) v})
                 (recur more))))))

(defn create-session!
  [session {:keys [user] :as _metadata}]
  (let [session-data (data-rows :session-id (:id session) (:data session))]
    (jdbc/with-transaction [txn *db*]
      (db/insert-sessions! txn [session])
      (db/insert-session-data! txn session-data)
      (when user
        (db/put-users! [user])))))

(defn identify-session!
  [{:keys [session-id user] :as _data} _metadata]
  (db/put-users! [user])
  (db/identify-session! session-id (:id user)))

(defn session-ended!
  [{:keys [id ended-at]} _metadata]
  (db/end-session! id (or ended-at (now))))

(defn- events->events-data
  [events]
  (loop [ans (transient [])
         [e & more] events]
    (if-not e
      (persistent! ans)
      (recur (data-rows ans :event-id (:id e) (:data e))
             more))))

(defn create-events!
  [{:keys [events]} _metadata]
  (let [event-data (events->events-data events)]
    (jdbc/with-transaction [txn *db*]
      (db/insert-events! txn events)
      (db/insert-event-data! txn event-data))))

(defn track!
  [req]
  (let [{:keys [data metadata]} (:body-params req)
        remote-ip (:kaiao/remote-ip req)
        f (case (-> metadata :op keyword)
            :kaiao.op/session-started (fn [data metadata]
                                        (-> data
                                            (assoc :ip-address remote-ip)
                                            (create-session! metadata)))
            :kaiao.op/identify identify-session!
            :kaiao.op/session-ended session-ended!
            :kaiao.op/events create-events!
            (constantly :kaiao/invalid-request))
        res (f data metadata)]
    (when (= :kaiao/invalid-request res)
      {:status 400
       :headers {}
       :body {:error "invalid request op"}})))
