(ns kaiao.domain
  (:require [clojure.spec.alpha :as s]))

(defn allowed-keys
  "returns a set of allowed keys for the given map spec"
  [spec-kw]
  (disj (->> (s/describe spec-kw)
             (filter vector?)
             (sequence cat)
             (map name)
             (map keyword)
             (into (sorted-set)))
        :created-at
        :updated-at))

(defn instant?
  [x]
  (instance? java.time.Instant x))

(s/def :kaiao/id uuid?)
(s/def :kaiao/project-id uuid?)
(s/def :kaiao/name string?)
(s/def :kaiao/domain string?)

(s/def :kaiao/created-at instant?) ;; when recorded in system
(s/def :kaiao/updated-at instant?) ;; when updated in system
(s/def :kaiao/started-at instant?) ;; actual event time
(s/def :kaiao/ended-at instant?) ;; actual event time
(s/def :kaiao/occurred-at instant?) ;; actual event time

(s/def :kaiao/user-id string?)
(s/def :kaiao/email string?)
(s/def :kaiao/first-name string?)
(s/def :kaiao/last-name string?)
(s/def :kaiao/tags (s/coll-of string? :distinct true))
(s/def :kaiao/project-version-id string?)
(s/def :kaiao/user-agent string?)
(s/def :kaiao/device-id string?)
(s/def :kaiao/ip-address string?)
(s/def :kaiao/session-id uuid?)
(s/def :kaiao/data map?)
(s/def :external/id string?)


(s/def :kaiao/project
  (s/keys :req-un [:kaiao/id
                   :kaiao/name]
          :opt-un [:kaiao/domain
                   :kaiao/data
                   :kaiao/tags
                   :kaiao/created-at
                   :kaiao/updated-at]))


(s/def :kaiao/user
  (s/keys :req-un [:external/id
                   :kaiao/project-id]
          :opt-un [:kaiao/email
                   :kaiao/first-name
                   :kaiao/last-name
                   :kaiao/data
                   :kaiao/tags
                   :kaiao/created-at
                   :kaiao/updated-at]))


(s/def :user-agent/family string?)
(s/def :user-agent/major string?)
(s/def :user-agent/minor string?)
(s/def :os/family string?)
(s/def :os/major string?)
(s/def :os/minor string?)
(s/def :device/family string?)

(s/def :kaiao/user-agent-data
  (s/keys :opt [:user-agent/family
                :user-agent/major
                :user-agent/minor
                :os/family
                :os/major
                :os/minor
                :device/family]))


(s/def :geo/iso-country-code string?)
(s/def :geo/subdivision string?)
(s/def :geo/city string?)
(s/def :geo/postal-code string?)
(s/def :geo/longitude double?)
(s/def :geo/latitude double?)

(s/def :kaiao/geo-data
  (s/keys :opt [:geo/iso-country-code
                :geo/subdivision
                :geo/city
                :geo/postal-code
                :geo/longitude
                :geo/latitude]))

(s/def :kaiao/session
  (s/keys :req-un [:kaiao/id
                   :kaiao/project-id
                   :kaiao/started-at]
          :opt-un [:kaiao/ended-at
                   :kaiao/user-id
                   :kaiao/project-version-id
                   :kaiao/user-agent
                   :kaiao/user-agent-data
                   :kaiao/device-id
                   :kaiao/ip-address
                   :kaiao/geo-data
                   :kaiao/data
                   :kaiao/tags
                   :kaiao/created-at
                   :kaiao/updated-at]))


(s/def :page/title string?)
(s/def :page/url string?)
(s/def :page/hostname string?)
(s/def :page/path string?)
(s/def :page/query string?)
(s/def :page/referrer-url string?)
(s/def :referrer/hostname string?)
(s/def :referrer/path string?)
(s/def :referrer/query string?)

(s/def :kaiao/page-data
  (s/keys :opt [:page/title
                :page/pathname
                :page/search
                :page/referrer
                :referrer/hostname
                :referrer/pathname
                :referrer/search]))


(s/def :kaiao/event
  (s/keys :req-un [:kaiao/id
                   :kaiao/project-id
                   :kaiao/session-id]
          :opt-un [:kaiao/name
                   :kaiao/data
                   :kaiao/tags
                   :kaiao/created-at
                   :kaiao/occurred-at]))

(def +allowed-user-keys+
  (allowed-keys :kaiao/user))

(def +allowed-session-keys+
  (allowed-keys :kaiao/session))

(def +allowed-event-keys+
  (allowed-keys :kaiao/event))
