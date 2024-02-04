(ns kaiao.ingest
  (:require
   [kaiao.db :as db]
   [kaiao.ext :as ext]
   [clojure.string :as str]
   [next.jdbc :as jdbc]
   [kaiao.system :refer [*db*]])
  (:import (ua_parser Parser)))

(set! *warn-on-reflection* true)


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
  (db/end-session! id (or ended-at (ext/now))))

(defn- events->events-data
  [events]
  (loop [ans (transient [])
         [e & more] events]
    (if-not e
      (persistent! ans)
      (recur (data-rows ans :event-id (:id e) (:data e))
             more))))

(defn create-events!
  [{:keys [events] :as _data} _metadata]
  (let [event-data (events->events-data events)]
    (jdbc/with-transaction [txn *db*]
      (db/insert-events! txn events)
      (db/insert-event-data! txn event-data))))



(defn- ua-info
  [ua-str]
  (let [p (Parser.)
        c (.parse p ua-str)
        ua (.-userAgent c)
        os (.-os c)
        device (.-device c)]
    (ext/remove-empties
     {:user-agent ua-str
      :user-agent-family (.-family ua)
      :user-agent-major (.-major ua)
      :user-agent-minor (.-minor ua)
      :os-family (.-family os)
      :os-major (.-major os)
      :os-minor (.-minor os)
      :device-family (.-family device)})))


(defn request-info
  [req]
  (let [remote-ip (:kaiao/remote-ip req)
        ua (get-in req [:headers "user-agent"])]
    (-> (ua-info ua)
        (assoc :ip-address remote-ip))))

(defn track!
  [req]
  (let [{:keys [data metadata]} (:body-params req)
        f (case (-> metadata :op keyword)
            :kaiao.op/session-started (fn [data metadata]
                                        (-> (request-info req)
                                            (merge data)
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

;; pull out os and user agent info
