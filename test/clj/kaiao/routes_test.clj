(ns kaiao.routes-test
  (:require
   [clojure.test :refer [deftest testing use-fixtures]]
   [clojure.edn :as edn]
   [expectations.clojure.test :refer [expect]]
   [kaiao.routes :as routes]
   [kaiao.main :as main]
   [kaiao.test :as test]))

(use-fixtures :each test/with-system)


(deftest prefix-url-rewrite-test
  (let [f (fn [uri]
            (-> {:scheme :https
                 :request-method "GET"
                 :uri uri
                 :headers {"accept" "application/edn"}}
                routes/router
                (select-keys [:status :body])
                (update :body edn/read-string)))]
    (testing "default"
      (binding [routes/*uri-rewrite-fn* (main/url-rewrite-fn "")]
        (expect {:status 200 :body {:ping :pong}} (f "/ping"))))
    (testing "prefix"
      (binding [routes/*uri-rewrite-fn* (main/url-rewrite-fn "/p1/p2")]
        (expect {:status 200 :body {:ping :pong}} (f "/p1/p2/ping"))))))
