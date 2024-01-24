(ns kaiao.util
  (:require [clojure.java.io :as io])
  (:import (java.util.jar Manifest)))



(defn git-sha
  "Returns the git-sha from the jar's MANIFEST.MF file if one exists else nil"
  []
  (some-> (io/resource "META-INF/MANIFEST.MF")
          io/input-stream
          (Manifest.)
          (.getMainAttributes)
          (.getValue "git-sha")))

(defn now
  []
  (java.time.Instant/now))


(defn remove-nils
  [m]
  (loop [ans (transient {})
         [[k v :as kv] & more] m]
    (if-not kv
      (persistent! ans)
      (recur (cond-> ans
               (some? v) (assoc! k v))
             more))))
