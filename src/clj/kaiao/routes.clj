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
   [clojure.string :as str]
   [kaiao.ingest]
   [cognitect.transit :as transit]))

(defonce ^:dynamic *https-required* true)
(defonce ^:dynamic *uri-rewrite-fn* identity)

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
          (assert encoder)
          (-> resp
              (assoc-in [:headers "content-type"] encoding)
              (update :body encoder)))
        resp))))


(defn- https?
  [req]
  (or (and *https-required*
           (= "https" (or (get-in req [:headers "x-forwarded-proto"])
                          (name (:scheme req)))))
      true))

(defn- remote-ip
  [req]
  ;; each load balancer can add its ip to the end of x-forwarded-for
  ;; the first ip is the actual remote ip
  (when-let [s (or (some-> (get-in req [:headers "x-forwarded-for"])
                           (str/split #",")
                           first
                           str/trim)
                   (:remote-addr req))]
    ;; ip6 remote-addrs can look like "[:::0]" this unwraps the []
    (if (= \[ (.charAt s 0))
      (subs s 1 (dec (count s)))
      s)))

(defn wrap-remote-ip
  [handler]
  (fn [req]
    (handler (assoc req :kaiao/remote-ip (remote-ip req)))))


(def ^:private +cors-headers+
  {"access-control-allow-methods" "GET, POST, OPTIONS"
   "access-control-allow-origin" "*"
   "access-control-allow-headers" "Content-Type"
   "access-control-max-age" "3600"})

(defn wrap-cors
  [handler]
  (fn [req]
    (if (= :options (:request-method req))
      {:status 204
       :headers +cors-headers+}
      (-> (handler req)
          (update :headers #(merge +cors-headers+ %))))))


(defn wrap-require-https
  "Require https otherwise returned fn returns nil which triggers
  `:not-acceptable` http status 406"
  [handler]
  (fn [req]
    (if (https? req)
      (handler req)
      {:status 406
       :headers {}
       :body {:error "https required"}})))

(defn wrap-exception-handler
  [handler]
  (fn [req]
    (try
      (handler req)
      (catch Throwable t
        (if (-> t ex-data :ex/tags :ex/validation)
          (do
            (mu/log :kaiao/validation-error
                    :exception t
                    :ex/message (ex-message t)
                    :ex/data (ex-data t))
            {:status 400
             :body {:error "validation error"}})
          (do
            (mu/log :kaiao/unhandled-exception :exception t)
            {:status 500
             :headers {"content-type" "application/json"}
             :body {:error "server error"}}))))))


(defn not-found
  [_req]
  {:status 404
   :headers {}
   :body {:error "not found"}})

(defn wrap-uri-rewrite
  [handler]
  (fn [req]
    (handler (*uri-rewrite-fn* req))))

(def routes
  {"GET /ping" pong
   "POST /track" #'kaiao.ingest/track!
   "* /**" not-found})




(def router
  (-> routes
      router/router
      ring.params/wrap-params
      wrap-uri-rewrite
      wrap-decode-request
      wrap-content-negotiation
      wrap-response
      wrap-remote-ip
      wrap-cors
      wrap-require-https
      wrap-exception-handler
      wrap-encode-response))
