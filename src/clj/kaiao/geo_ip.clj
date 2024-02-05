(ns kaiao.geo-ip
  (:require [clojure.java.io :as io])
  (:import (com.maxmind.geoip2 DatabaseReader DatabaseReader$Builder)
           (com.maxmind.db CHMCache)
           (java.net InetAddress)))

(set! *warn-on-reflection* true)

;; this reader is thread safe
(defonce ^{:tag DatabaseReader} +database-reader+ nil)


(defn init!
  [db-file]
  (let [rdr (-> (DatabaseReader$Builder. (io/file db-file))
                (.withCache (CHMCache.))
                (.build))]
    (alter-var-root #'+database-reader+ (constantly rdr))))

(defn available?
  []
  (some? +database-reader+))

(defn city
  [ip]
  (when +database-reader+
    (let [ip-addr (InetAddress/getByName ip)
          r (.city +database-reader+ ip-addr)
          l (.getLocation r)]
      {:iso-country-code (.getIsoCode (.getCountry r))
       :least-specific-subdivision (.getName (.getLeastSpecificSubdivision r))
       :most-specific-subdivision (.getName (.getMostSpecificSubdivision r))
       :city (.getName (.getCity r))
       :postal-code (.getCode (.getPostal r))
       :latitude (.getLatitude l)
       :longitude (.getLongitude l)})))
