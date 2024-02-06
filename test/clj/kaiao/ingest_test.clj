(ns kaiao.ingest-test
  (:require
   [clojure.test :refer [deftest use-fixtures]]
   [expectations.clojure.test :refer [expect]]
   [kaiao.test :as test]
   [kaiao.domain :as domain]
   [kaiao.db :as db]))

(use-fixtures :each test/with-system)

(def +user-agent+ "Mozilla/5.0 (X11; Linux i686; rv:122.0) Gecko/20100101 Firefox/122.0")
(def +ip-address+ "38.15.220.202")

(deftest create-session!-test
  (let [session-id  #uuid "48ebd7f0-9408-4803-a808-1a1b08e4d9b3"
        m {:id session-id
           :project-id #uuid "1e891261-7208-4aa6-a9aa-76d64b274f05"
           :data {:baked-good "scone"
                  :quantity 20
                  :price-each 3.5
                  :tags ["sweet" "savory" "buttery"]}}
        {:keys [status]} (test/track-request
                          :headers {"user-agent" +user-agent+
                                    "x-forwarded-for" +ip-address+}
                          :body {:metadata {:op :kaiao.op/session-started}
                                 :data m})]
    (expect 204 status)

    (let [found (-> (db/get-session session-id)
                    (select-keys domain/+allowed-session-keys+))]
      (expect {:geo/iso-country-code "US"
               :geo/subdivision "California"
               :geo/city "Los Angeles"
               :geo/postal-code "90059"
               :geo/latitude 33.9322
               :geo/longitude -118.2488}
              (:geo-data found))

      (expect {:os/family "Linux",
               :device/family "Other",
               :user-agent/major "122",
               :user-agent/minor "0",
               :user-agent/family "Firefox"}
              (:user-agent-data found))

      (expect {:id session-id
               :project-id #uuid "1e891261-7208-4aa6-a9aa-76d64b274f05"
               :user-agent +user-agent+
               :ip-address +ip-address+
               :data {:baked-good "scone"
                      :quantity 20
                      :price-each 3.5
                      :tags ["sweet" "savory" "buttery"]}}
              (-> found
                  (select-keys domain/+allowed-session-keys+)
                  (dissoc :created-at :started-at :geo-data :user-agent-data)))

      (expect inst? (:started-at found))
      (expect nil (:ended-at found)))))


(deftest identify-sesion!-test
  (let [session-id  #uuid "48ebd7f0-9408-4803-a808-1a1b08e4d9b3"
        project-id #uuid "1e891261-7208-4aa6-a9aa-76d64b274f05"
        user {:id "LD"
              :project-id project-id
              :first-name "larry"
              :last-name "david"
              :email "larry.david@example.com"}
        m {:id session-id
           :project-id project-id
           :os "linux"
           :user-agent "firefox"}]
    (test/track-request :body {:metadata {:op :kaiao.op/session-started}
                               :data m})
    (expect {:id session-id} (select-keys (db/get-session session-id)
                                          [:id :user-id]))
    (expect nil (db/get-user project-id "LD"))
    (test/track-request :body {:metadata {:op :kaiao.op/identify}
                               :data {:session-id session-id
                                      :user user}})
    (expect "LD" (:user-id (db/get-session session-id)))
    (expect user (-> (db/get-user project-id "LD")
                     (select-keys (keys user))))))

(deftest sesion-ended!-test
  (let [session-id  #uuid "48ebd7f0-9408-4803-a808-1a1b08e4d9b3"
        project-id #uuid "1e891261-7208-4aa6-a9aa-76d64b274f05"
        m {:id session-id
           :project-id project-id
           :os "linux"
           :user-agent "firefox"}]
    (test/track-request :body {:metadata {:op :kaiao.op/session-started}
                               :data m})
    (expect {:id session-id} (select-keys (db/get-session session-id)
                                          [:id :user-id]))
    (test/track-request :body {:metadata {:op :kaiao.op/session-ended
                                          :project-id project-id}
                               :data {:id session-id
                                      :ended-at (System/currentTimeMillis)}})
    (expect inst? (:ended-at (db/get-session session-id)))))


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
    (test/track-request :body {:metadata {:op :kaiao.op/events}
                               :data {:events [m]}})
    (expect m (-> (db/get-event event-id)
                  (select-keys (keys m))))))
