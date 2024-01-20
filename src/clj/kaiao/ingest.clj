(ns kaiao.ingest
  (:require [kaiao.db :as db]))


(defn create-session!
  [req]
  (db/insert-sessions! [(:body-params req)]))



(defn identify-session!
  [req]
  (let [session-id (get-in req [:path-params 0])
        user-id (get-in req [:body-params :user-id])]
    (db/identify-session! session-id user-id)))


(defn track!
  [req]
  (db/insert-events! (:body-params req)))
