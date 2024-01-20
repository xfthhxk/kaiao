(ns kaiao.routes
  (:require
   [clj-simple-router.core :as router]
   [com.brunobonacci.mulog :as mu]
   [ring.middleware.content-type]
   [ring.middleware.params :as ring.params]
   [ring.util.response :as response]
   [s-exp.hirundo.http.response]
   [s-exp.hirundo.http.routing]
   [charred.api :as charred]
   [clojure.edn :as edn]
   [kaiao.ingest]
   [cognitect.transit :as transit]))


(defn <-transit
  [in]
  (let [in (if (string? in)
             (java.io.ByteArrayInputStream. (.getBytes ^String in))
             in)]
    (transit/read (transit/reader in :json))))

(defn ->transit
  [data]
  (let [out (java.io.ByteArrayOutputStream.)]
    (transit/write (transit/writer out :json) data)
    (str out)))


(defn <-edn
  [in]
  (let [in (if (string? in)
             in
             (slurp in))]
    (edn/read-string in)))


(def encoding->decoder
  {"application/json" (charred/parse-json-fn {:key-fn keyword})
   "application/edn" <-edn
   "application/transit+json" <-transit})

(def encoding->encoder
  {"application/json" charred/write-json-str
   "application/edn" pr-str
   "application/transit+json" ->transit})


(def +encodings+
  (-> encoding->decoder
      keys
      set))

(defn pong
  [_req]
  {:status 200 :headers {} :body {:ping :pong}})


(defn acceptable-encodings?
  [{:keys [headers body] :as _req}]
  (let [{:strs [content-type accept]} headers]
    (and (or (and body (+encodings+ content-type))
             (not content-type))
         (or (not accept)
             (+encodings+ accept)
             (= "*/*" accept)))))


(defn wrap-content-negotiation
  [handler]
  (fn [req]
    (if (acceptable-encodings? req)
      (handler req)
      {:status 400
       :headers {}
       :body {:error "content-type and or accept headers are invalid"}})))

(defn wrap-decode-request
  [handler]
  (fn [{:keys [body] :as req}]
    (handler
     (cond-> req
       body (assoc :body-params
                   ((encoding->decoder (get-in req [:headers "content-type"]))
                    body))))))

(defn wrap-response
  [handler]
  (fn [req]
    (let [resp (handler req)]
      (cond
        (response/response? resp) resp
        (not resp) {:status 204 :headers {}}
        :else {:status 200
               :headers {}
               :body resp}))))

(defn wrap-encode-response
  [handler]
  (fn [req]
    (let [resp (handler req)]
      (if (:body resp)
        (let [encoding (or (get-in resp [:headers "content-type"])
                           (+encodings+ (get-in req [:headers "accept"]))
                           "application/json")
              encoder (encoding->encoder encoding)]
          (-> resp
              (assoc-in [:headers "content-type"] encoding)
              (update :body encoder)))
        resp))))


(defn wrap-exception-handler
  [handler]
  (fn [req]
    (try
      (handler req)
      (catch Throwable t
        (mu/log :kaiao/unhandled-exception :exception t)
        {:status 500
         :headers {"content-type" "text/plain"}
         :body "server error"}))))



(defn not-found
  [_req]
  {:status 404
   :headers {}
   :body {:error "not found"}})

(def routes
  {"GET /ping" pong
   "POST /sessions" #'kaiao.ingest/create-session!
   "PUT /sessions/*" #'kaiao.ingest/identify-session!
   "POST /events:batch-create" #'kaiao.ingest/track!
   "* /**" not-found})


(def router
  (-> routes
      router/router
      ring.params/wrap-params
      wrap-decode-request
      wrap-content-negotiation
      wrap-response
      wrap-exception-handler
      wrap-encode-response))
