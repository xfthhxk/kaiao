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
