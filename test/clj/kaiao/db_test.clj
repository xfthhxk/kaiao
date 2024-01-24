(ns kaiao.db-test
  (:require
   [clojure.test :refer [deftest use-fixtures]]
   [expectations.clojure.test :refer [expect]]
   [kaiao.test :as test]
   [kaiao.db :as db]))

(use-fixtures :each test/with-system)

(deftest create-project!-test
  (let [id #uuid "b2c33bd3-32f6-4446-8add-3473192e26d4"
        p {:id id
           :name "proj-1"
           :domain "example.com"}]
    (db/create-project! p)
    (expect p (-> (db/get-project id)
                  (select-keys (keys p))))))

(deftest put-users!-test
  (let [project-id #uuid "b2c33bd3-32f6-4446-8add-3473192e26d4"
        user-id "user-id-1"
        u {:project-id project-id
           :user-id  user-id
           :email "user@example.com"
           :first-name "User First"
           :last-name "User Last"
           :name "boo"
           :org-id "org-1"
           :org-name "Org 1 Inc."
           :tags ["hello" "bye"]}]
    (db/put-users! [u])
    (expect u (-> (db/get-user project-id user-id)
                  (select-keys (keys u))))
    (expect inst? (:created-at (db/get-user project-id user-id)))))


(deftest insert-sessions!-test
  (let [id #uuid "55d26c2c-dac9-43ed-afaa-ed0e4f84d706"
        s {:id id
           :project-id #uuid "b2c33bd3-32f6-4446-8add-3473192e26d4"
           :hostname "my-host"
           :browser "chrome"
           :os "linux"
           :device "Mac"
           :screen "1200x1800"
           :language "en"
           :ip-address "127.0.0.1"
           :country "us"
           :city "santa fe"}]
    (db/insert-sessions! [s])
    (expect s (-> (db/get-session id)
                  (select-keys (keys s))))
    (expect inst? (-> id db/get-session :created-at))))

(deftest identify-session!-test
  (let [id #uuid "55d26c2c-dac9-43ed-afaa-ed0e4f84d706"
        s {:id id
           :project-id #uuid "b2c33bd3-32f6-4446-8add-3473192e26d4"
           :hostname "my-host"
           :browser "chrome"
           :os "linux"
           :device "Mac"
           :screen "1200x1800"
           :language "en"
           :ip-address "127.0.0.1"
           :country "us"
           :city "santa fe"}]
    (db/insert-sessions! [s])
    (expect some? (db/get-session id))
    (expect nil (:user-id (db/get-session id)))
    (db/identify-session! id "42")
    (expect "42" (:user-id (db/get-session id)))))



(deftest insert-events!-test
  (let [id #uuid "3cc7b1a0-fecc-44ed-ad16-59880b02c503"
        e {:id id
           :project-id #uuid "b2c33bd3-32f6-4446-8add-3473192e26d4"
           :session-id #uuid "55d26c2c-dac9-43ed-afaa-ed0e4f84d706"
           :name "user/login"
           :url-path "/login"
           :url-query "?a=1"
           :referrer-path "/ref/path"
           :referrer-query "?b=2"
           :referrer-domain "example.com"
           :page-title "System Login"}]
    (db/insert-events! [e])
    (expect e (-> (db/get-event id)
                  (select-keys (keys e))))
    (expect inst? (-> id db/get-event :created-at))))


(deftest insert-event-data!-test
  (let [id #uuid "3cc7b1a0-fecc-44ed-ad16-59880b02c503"
        e {:event-id id
           :key "boo"
           :string-value "hoo"}]
    (db/insert-event-data! [e])
    (expect e (-> (db/get-event-data id "boo")
                  (select-keys (keys e))))
    (expect inst? (:created-at (db/get-event-data id "boo")))))


(deftest insert-session-data!-test
  (let [id #uuid "3cc7b1a0-fecc-44ed-ad16-59880b02c503"
        e {:session-id id
           :key "boo"
           :string-value "hoo"}]
    (db/insert-session-data! [e])
    (expect e (-> (db/get-session-data id "boo")
                  (select-keys (keys e))))
    (expect inst? (:created-at (db/get-session-data id "boo")))))
