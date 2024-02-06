(ns kaiao.geo-ip
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [kaiao.ext :as ext])
  (:import (com.maxmind.geoip2 DatabaseReader DatabaseReader$Builder)
           (com.maxmind.geoip2.exception AddressNotFoundException)
           (com.maxmind.db CHMCache)
           (java.net InetAddress UnknownHostException)))

(set! *warn-on-reflection* true)

;; this reader is thread safe
(defonce ^{:tag DatabaseReader} +database-reader+ nil)

(defn shutdown!
  []
  (when +database-reader+
    (.close +database-reader+)
    (alter-var-root #'+database-reader+ (constantly nil))))

(defn init!
  [db-file]
  (when +database-reader+
    (shutdown!))
  (let [rdr (-> (DatabaseReader$Builder. (io/file db-file))
                (.withCache (CHMCache.))
                (.build))]
    (alter-var-root #'+database-reader+ (constantly rdr))))



(defn available?
  []
  (some? +database-reader+))

(defn city
  [ip]
  (when (and +database-reader+ (not (str/blank? ip)))
    (try
      (let [ip-addr (InetAddress/getByName ip)
            r (.city +database-reader+ ip-addr)
            l (.getLocation r)]
        (ext/remove-empties
         {:geo/iso-country-code (.getIsoCode (.getCountry r))
          :geo/subdivision (.getName (.getMostSpecificSubdivision r))
          :geo/city (.getName (.getCity r))
          :geo/postal-code (.getCode (.getPostal r))
          :geo/latitude (.getLatitude l)
          :geo/longitude (.getLongitude l)}))
      (catch AddressNotFoundException _)
      (catch UnknownHostException _))))
