(ns kaiao.ingest
  (:require
   [clojure.string :as str]
   [kaiao.db :as db]
   [kaiao.geo-ip :as geo-ip]
   [kaiao.ext :as ext]
   [next.jdbc :as jdbc]
   [kaiao.system :refer [*db*]])
  (:import (ua_parser Parser)))

(set! *warn-on-reflection* true)


(defn create-session!
  [session {:keys [user] :as _metadata}]
  (jdbc/with-transaction [txn *db*]
    (db/insert-sessions! txn [session])
    (when user
      (db/put-users! [user]))))

(defn identify-session!
  [{:keys [session-id user] :as _data} _metadata]
  (db/put-users! [user])
  (db/identify-session! session-id (:id user)))

(defn session-ended!
  [{:keys [id ended-at]} _metadata]
  (db/end-session! id (or ended-at (ext/now))))

(defn create-events!
  [{:keys [events] :as _data} _metadata]
  (db/insert-events! events))

(defn- ua-info
  [ua-str]
  (when-not (str/blank? ua-str)
    (let [p (Parser.)
          c (.parse p ua-str)
          ua (.-userAgent c)
          os (.-os c)
          device (.-device c)]
      {:user-agent ua-str
       :user-agent-data
       (ext/remove-empties
        {:user-agent/family (.-family ua)
         :user-agent/major (.-major ua)
         :user-agent/minor (.-minor ua)
         :os/family (.-family os)
         :os/major (.-major os)
         :os/minor (.-minor os)
         :device/family (.-family device)})})))

(defn request-info
  [req]
  (let [remote-ip (:kaiao/remote-ip req)
        ua (get-in req [:headers "user-agent"])]
    (-> (ua-info ua)
        (assoc :ip-address remote-ip)
        (assoc :geo-data (geo-ip/city remote-ip)))))

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
