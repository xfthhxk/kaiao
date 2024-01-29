(ns kaiao.ingest-test
  (:require
   [clojure.test :refer [deftest use-fixtures testing]]
   [clojure.set :as set]
   [expectations.clojure.test :refer [expect]]
   [kaiao.test :as test]
   [kaiao.domain :as domain]
   [kaiao.ingest :as ingest]
   [kaiao.db :as db]))

(use-fixtures :each test/with-system)

(deftest data-rows-test
  (expect [{:event-id "23"
            :key :a
            :int-value 1
            :created-at "2024-01-23T20:18:10.735063053Z"}
           {:event-id "23"
            :key :b
            :string-value "string"
            :created-at "2024-01-23T20:18:10.735063053Z"}
           {:event-id "23"
            :key :c
            :string-value :key
            :created-at "2024-01-23T20:18:10.735063053Z"}]
          (#'ingest/data-rows :event-id "23" "2024-01-23T20:18:10.735063053Z"
                              {:a 1
                               :b "string"
                               :c :key})))



(deftest create-session!-test
  (let [session-id  #uuid "48ebd7f0-9408-4803-a808-1a1b08e4d9b3"
        m {:id session-id
           :project-id #uuid "1e891261-7208-4aa6-a9aa-76d64b274f05"
           :os "linux"
           :browser "firefox"
           :ip-address "127.0.0.1"
           :data {:baked-good "scone"
                  :quantity 20
                  :price-each 3.5
                  :tags ["sweet" "savory" "buttery"]}}]
    (ingest/create-session! m {})
    (expect {:id session-id
             :project-id #uuid "1e891261-7208-4aa6-a9aa-76d64b274f05"
             :os "linux"
             :browser "firefox"
             :ip-address "127.0.0.1"}
            (-> (db/get-session session-id)
                (select-keys domain/+allowed-session-keys+)
                (dissoc :created-at)))
    (expect {:session-id session-id
             :key "baked-good"
             :string-value "scone"}
            (-> (db/get-session-data session-id :baked-good)
                (select-keys [:session-id :key :string-value])))
    (let [found (db/get-session-data session-id)]
      (expect #{{:session-id session-id
                 :key "baked-good"
                 :string-value "scone"}
                {:session-id session-id
                 :key "quantity"
                 :int-value 20}
                {:session-id session-id
                 :key "price-each"
                 :decimal-value 3.5000M}
                {:session-id session-id
                 :key "tags"
                 :json-value ["sweet" "savory" "buttery"]}}
              (set/project found [:session-id :key :string-value :int-value :decimal-value :json-value]))
      (expect true (every? inst? (map :created-at found))))))


(deftest identify-sesion!-test
  (let [session-id  #uuid "48ebd7f0-9408-4803-a808-1a1b08e4d9b3"
        project-id #uuid "1e891261-7208-4aa6-a9aa-76d64b274f05"
        m {:id session-id
           :project-id project-id
           :os "linux"
           :browser "firefox"}]
    (ingest/create-session! m {})
    (expect nil (:user-id (db/get-session session-id)))
    (ingest/identify-session! {:session-id session-id
                               :user {:id "LD"
                                      :project-id project-id
                                      :first-name "larry"
                                      :last-name "david"
                                      :email "larry.david@example.com"}}
                              {})
    (expect "LD" (:user-id (db/get-session session-id)))))


(deftest create-events!-test
  (let [event-id  #uuid "48ebd7f0-9408-4803-a808-1a1b08e4d9b3"
        m {:id event-id
           :project-id #uuid "1e891261-7208-4aa6-a9aa-76d64b274f05"
           :session-id #uuid "3293bd86-b6af-454f-a3fb-f00926fe133b"
           :name "add-to-cart"
           :url-path "/ginger-pear-scone"
           :page-title "Ginger Pear Scone"
           :data {:baked-good "scone"
                  :quantity 20
                  :price-each 3.5
                  :tags ["sweet" "savory" "buttery"]}}]
    (ingest/create-events! [m] {})
    (expect some? (db/get-event event-id))
    (expect {:event-id event-id
             :key "baked-good"
             :string-value "scone"}
            (-> (db/get-event-data event-id :baked-good)
                (select-keys [:event-id :key :string-value])))
    (let [found (db/get-event-data event-id)]
      (expect #{{:event-id event-id
                 :key "baked-good"
                 :string-value "scone"}
                {:event-id event-id
                 :key "quantity"
                 :int-value 20}
                {:event-id event-id
                 :key "price-each"
                 :decimal-value 3.5000M}
                {:event-id event-id
                 :key "tags"
                 :json-value ["sweet" "savory" "buttery"]}}
              (set/project found [:event-id :key :string-value :int-value :decimal-value :json-value]))
      (expect true (every? inst? (map :created-at found))))))
