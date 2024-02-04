(ns kaiao.ext
  (:require [clojure.string :as str])
  (:import (java.time Instant)))

(set! *warn-on-reflection* true)

(defn remove-empties
  [m]
  (loop [ans (transient (empty m))
         [[k v :as kv] & more] m]
    (if-not kv
      (persistent! ans)
      (let [skip? (or (nil? v)
                      (and (string? v) (str/blank? v))
                      (and (coll? v) (empty? v)))
            ans (if skip? ans (assoc! ans k v))]
        (recur ans more)))))

(defn now
  []
  (java.time.Instant/now))

(defn coerce-instant
  [x]
  (cond
    (inst? x) x
    (int? x) (Instant/ofEpochMilli x)
    (string? x) (Instant/parse x)
    :else nil))

(defn coerce-uuid
  [x]
  (cond
    (uuid? x) x
    (string? x) (java.util.UUID/fromString x)
    :else nil))
