(ns kaiao.domain
  (:require [clojure.spec.alpha :as s]))

(defn allowed-keys
  "returns a set of allowed keys for the given map spec"
  [spec-kw]
  (->> (s/describe spec-kw)
       (filter vector?)
       (sequence cat)
       (map name)
       (map keyword)
       (into (sorted-set))))

(defn instant?
  [x]
  (instance? java.time.Instant x))

(s/def :kaiao/id uuid?)
(s/def :kaiao/project-id uuid?)
(s/def :kaiao/name string?)
(s/def :kaiao/domain string?)
(s/def :kaiao/created-at instant?)
(s/def :kaiao/updated-at instant?)
(s/def :kaiao/user-id string?)
(s/def :kaiao/email string?)
(s/def :kaiao/first-name string?)
(s/def :kaiao/last-name string?)
(s/def :kaiao/org-id string?)
(s/def :kaiao/org-name string?)
(s/def :kaiao/tags (s/coll-of string? :distinct true))
(s/def :kaiao/project-version-id string?)
(s/def :kaiao/hostname string?)
(s/def :kaiao/browser string?)
(s/def :kaiao/os string?)
(s/def :kaiao/device string?)
(s/def :kaiao/screen-height pos-int?)
(s/def :kaiao/screen-width pos-int?)
(s/def :kaiao/language string?)
(s/def :kaiao/ip-address string?)
(s/def :kaiao/country string?)
(s/def :kaiao/city string?)
(s/def :kaiao/subdivision-1 string?)
(s/def :kaiao/subdivision-2 string?)
(s/def :kaiao/session-id uuid?)
(s/def :kaiao/url-path string?)
(s/def :kaiao/url-query string?)
(s/def :kaiao/referrer-path string?)
(s/def :kaiao/referrer-query string?)
(s/def :kaiao/referrer-host string?)
(s/def :kaiao/page-title string?)
(s/def :kaiao/page-title string?)
(s/def :kaiao/event-id uuid?)
(s/def :kaiao/key string?)
(s/def :kaiao/string-value string?)
(s/def :kaiao/int-value int?)
(s/def :kaiao/decimal-value double?)
(s/def :kaiao/timestamp-value instant?)
(s/def :kaiao/json-value coll?)

(s/def :external/id string?)


(s/def :kaiao/project
  (s/keys :req-un [:kaiao/id
                   :kaiao/name]
          :opt-un [:kaiao/domain
                   :kaiao/created-at
                   :kaiao/updated-at]))


(s/def :kaiao/user
  (s/keys :req-un [:external/id
                   :kaiao/project-id]
          :opt-un [:kaiao/email
                   :kaiao/first-name
                   :kaiao/last-name
                   :kaiao/name
                   :kaiao/org-id
                   :kaiao/org-name
                   :kaiao/tags
                   :kaiao/created-at
                   :kaiao/updated-at]))

(s/def :kaiao/session
  (s/keys :req-un [:kaiao/id
                   :kaiao/project-id]
          :opt-un [:kaiao/user-id
                   :kaiao/project-version-id
                   :kaiao/hostname
                   :kaiao/browser
                   :kaiao/os
                   :kaiao/device
                   :kaiao/screen-height
                   :kaiao/screen-width
                   :kaiao/language
                   :kaiao/ip-address
                   :kaiao/country
                   :kaiao/city
                   :kaiao/subdivision-1
                   :kaiao/subdivision-2
                   :kaiao/created-at
                   :kaiao/updated-at]))


(s/def :kaiao/event
  (s/keys :req-un [:kaiao/id
                   :kaiao/project-id
                   :kaiao/session-id]
          :opt-un [:kaiao/name
                   :kaiao/url-path
                   :kaiao/url-query
                   :kaiao/referrer-path
                   :kaiao/referrer-query
                   :kaiao/referrer-host
                   :kaiao/page-title
                   :kaiao/created-at]))

(s/def :kaiao/event-data
  (s/keys :req-un [:kaiao/event-id
                   :kaiao/key]
          :opt-un [:kaiao/string-value
                   :kaiao/int-value
                   :kaiao/decimal-value
                   :kaiao/timestamp-value
                   :kaiao/json-value
                   :kaiao/created-at]))

(s/def :kaiao/session-data
  (s/keys :req-un [:kaiao/session-id
                   :kaiao/key]
          :opt-un [:kaiao/string-value
                   :kaiao/int-value
                   :kaiao/decimal-value
                   :kaiao/timestamp-value
                   :kaiao/json-value
                   :kaiao/created-at]))

(def +allowed-user-keys+
  (allowed-keys :kaiao/user))

(def +allowed-session-keys+
  (allowed-keys :kaiao/session))

(def +allowed-event-keys+
  (allowed-keys :kaiao/event))

(def +allowed-event-data-keys+
  (allowed-keys :kaiao/event-data))

(def +allowed-session-data-keys+
  (allowed-keys :kaiao/session-data))
