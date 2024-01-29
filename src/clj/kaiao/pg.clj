(ns kaiao.pg
  (:require [charred.api :as charred]
            [clojure.java.io :as io]
            [next.jdbc.prepare :as prepare]
            [next.jdbc.result-set :as result-set]
            [clojure.string :as str])
  (:import (java.io InputStream)
           (java.sql Connection Timestamp PreparedStatement)
           (java.time Instant)
           (java.util HexFormat)
           (org.postgresql PGConnection)
           (org.postgresql.copy CopyManager)
           (org.postgresql.util PGobject)))


(set! *warn-on-reflection* true)

(def ^:private parse-json (charred/parse-json-fn {:key-fn keyword}))

(defn ->jsonb
  [x]
  (doto (PGobject.)
    (.setType "jsonb")
    (.setValue (charred/write-json-str x))))

(defn <-json
  [^PGobject o]
  (let [v (.getValue o)]
    (case (.getType o)
      ("json" "jsonb") (parse-json v)
      v)))

(extend-protocol prepare/SettableParameter
  clojure.lang.IPersistentMap
  (set-parameter [^clojure.lang.IPersistentMap v ^PreparedStatement ps ^long i]
    (.setObject ps i (->jsonb v)))

  clojure.lang.IPersistentVector
  (set-parameter [^clojure.lang.IPersistentVector v ^PreparedStatement ps ^long i]
    (.setObject ps i (->jsonb v)))

  clojure.lang.IPersistentList
  (set-parameter [^clojure.lang.IPersistentList v ^PreparedStatement ps ^long i]
    (.setObject ps i (->jsonb v)))

  clojure.lang.IPersistentSet
  (set-parameter [^clojure.lang.IPersistentSet v ^PreparedStatement ps ^long i]
    (.setObject ps i (->jsonb v)))

  clojure.lang.Keyword
  (set-parameter [^clojure.lang.Keyword v ^PreparedStatement ps ^long i]
    (.setString ps i (str (.-sym v))))

  java.time.Instant
  (set-parameter [^java.time.Instant v ^PreparedStatement ps ^long i]
    (.setTimestamp ps i (Timestamp/from v))))


(extend-protocol result-set/ReadableColumn
  PGobject
  (read-column-by-label [v _label]
    (<-json v))
  (read-column-by-index [v _rsmeta _idx]
    (<-json v)))


;; ----------------------------------------------------------------------
;; Copy Command
;; ----------------------------------------------------------------------

(def ^:const +default-delimiter+ (char 31))
(def ^:const +default-quote+ (char 1))

(def +default-delimiter-pattern+ (re-pattern (str +default-delimiter+)))

(def cn? (partial instance? java.sql.Connection))

(def pg-cn? (partial instance? PGConnection))


(defn- unwrapped-cn
  "Returns the unwrapped PGConnection."
  [^java.sql.Connection cn]
  (assert (cn? cn) "Expected a java.sql.Connection at this point.")
  (.unwrap cn PGConnection))


;; https://stackoverflow.com/questions/28568747/using-ascii-31-field-separator-character-as-postgresql-copy-delimiter

(defn- handle-java-escape
  "This is needed to write out to a file.  The extra ones are there because
   the file writer itself will escape the backslashes. "
  [s]
  (str/replace s "\\" "\\\\"))


(defn- hex-value
  "x is either int or char"
  [x]
  (if (string? x) x
      (let [hex (->> x int Integer/toHexString)]
        (str "E'\\x" hex \'))))


(defn copy-in-command
  "Generates a copy command used by the CopyManager. Uses tabs as the field separator by default."
  [& {:keys [table columns delimiter quote]}]
  (cond-> (format "copy %s (%s) from STDIN" (name table) (str/join ", " columns))
    delimiter (str " CSV delimiter " (hex-value delimiter))
    quote (str " quote " (hex-value quote))))


(defn bytes->hex-for-copy-in
  [^bytes bs]
  (str "\\x" (.formatHex (HexFormat/of) bs)))


(defn copy-out-command
  "Generates a copy command used by the CopyManager."
  [& {:keys [sql delimiter quote]
      :or {delimiter +default-delimiter+
           quote +default-quote+}}]
  (format "copy (%s) to STDOUT CSV delimiter %s quote %s"
          sql
          (hex-value delimiter)
          (hex-value quote)))


(defn- copy-in-stream*
  "Uses postgres CopyManager to bulk load data to a table. Returns the count
   of rows inserted via copy.
   pre-copy-fn gets the connection, post-copy-fn gets connection and count"
  [^Connection cn ^String copy-cmd ^InputStream input-stream
   & {:keys [pre-copy-fn post-copy-fn]
      :or {pre-copy-fn identity
           post-copy-fn (constantly nil)}}]
  (let [pg-cn (unwrapped-cn cn)
        _ (pre-copy-fn pg-cn)
        copy-mgr (CopyManager. pg-cn)
        n (.copyIn copy-mgr copy-cmd input-stream)]
    (post-copy-fn pg-cn n)
    n))


(defn- copy-in*
  [cn copy-cmd input-source opts]
  (if (instance? InputStream input-source)
    (copy-in-stream* cn copy-cmd input-source opts)
    (with-open [^InputStream fis (io/input-stream input-source)]
      (copy-in-stream* cn copy-cmd fis opts))))



(defn copy-in
  "`cn` is a Connection. `input-source` is
  either a stream or something that can be turned into a path via fs/path
  copy in direct to the table specified. `opts` is a map with keys
  `pre-copy-fn` and `post-copy-fn`. Returns count of records copied."
  ([cn copy-cmd input-source]
   (copy-in cn copy-cmd input-source {}))
  ([cn copy-cmd input-source opts]
   (copy-in* cn copy-cmd input-source opts)))


(defn escape-special-chars
  [s]
  (some-> s
          (str/escape  {\newline   "\\n"
                        \return    "\\r"})))


(defn escape-hex
  [s]
  (str "\\x" s))


(defn- delimit
  [delimiter xs]
  (->> xs
       (map str)
       (map handle-java-escape)
       (str/join delimiter)))


(defn delimited-string
  "Returns a newline terminated string of ordered values from `m` based on
  `columns` delimited by `delimiter`."
  ^String [columns delimiter m]
  (str (->> (map m columns)
            (map (fn [x]
                   (cond-> x
                     (string? x) escape-special-chars)))
            (delimit delimiter))
       \newline))


(defn spit-for-copy-in!
  "create a `file` with data from `ms`."
  ([file columns ms]
   (spit-for-copy-in! file columns +default-delimiter+ ms))
  ([file columns delimiter ms]
   (with-open [writer (io/writer file)]
     (doseq [m ms :when m]
       (->> m
            (delimited-string columns delimiter)
            (.write writer))))))


(defn copy-out-stream*
  [^Connection cn ^String copy-cmd ^java.io.OutputStream output-stream]
  (let [pg-cn (unwrapped-cn cn)
        copy-mgr (CopyManager. pg-cn)
        n (.copyOut copy-mgr copy-cmd output-stream)]
    n))
