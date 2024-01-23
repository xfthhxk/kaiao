(ns kaiao.ingest
  (:require [kaiao.db :as db]
            [kaiao.routes :as routes]))



(defn create-session!
  [req]
  (db/insert-sessions! [(:body-params req)]))



(defn identify-session!
  [req]
  (let [session-id (get-in req [:path-params 0])
        project-id (get-in req [:body-params :project-id])
        project-id (get-in req [:body-params :project-id])
        user (get-in req [:body-params :user])
        op (get-in req [:body-params :operation])]
    ;; put user
    (db/put-users! [user])
    ;(db/identify-session! session-id user-id)
    )
  )


(defn track!
  [req]
  (db/insert-events! (:body-params req)))


(comment

  (routes/router
   {:headers
    {"accept" "*/*", "host" "localhost:8080", "user-agent" "curl/7.81.0"},
    :server-port 8080,
    :uri "/ping",
    :server-name "127.0.0.1",
    :scheme :http,
    :request-method :get}))
