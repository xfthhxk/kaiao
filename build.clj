(ns build
  (:require [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as dd]
            [clojure.pprint :as pprint]))

(def lib 'xfthhxk/kaiao)
(def version (format "0.1.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def jar-file "target/kaiao.jar")
(def uber-jar-file "target/kaiao-uber.jar")
(def clj-src-dirs ["src/clj"])
(def java-src-dirs ["src/java"])

(defn git-sha
  []
  (b/git-process {:git-args "rev-parse --short HEAD"}))

(defonce ^:dynamic *basis* (delay nil))

(defn set-basis!
  [m]
  (let [d (-> {:project "deps.edn"}
              (merge m)
              b/create-basis
              delay)]
    (alter-var-root #'*basis* (constantly d))))

(set-basis! {})

(defn show-defaults [_]
  (println "default-basis:")
  (pprint/pprint @*basis*)
  (println "target:" "target")
  (println "class-dir:" class-dir)
  (println "jar-file:" jar-file))

(defn clean [_]
  (println "Cleaning ...")
  (b/delete {:path "target"}))

(defn compile-clj [_]
  (println "Compiling clj...")
  (b/compile-clj {:src-dirs clj-src-dirs
                  :class-dir class-dir
                  :basis @*basis*}))


(defn- pom-template [version]
  [[:description "Kaiao can be used to track in-app events for later analysis."]
   [:url "https://github.com/xfthhxk/kaiao"]
   [:licenses
    [:license
     [:name "MIT License"]
     [:url "https://opensource.org/license/mit/"]]]
   [:developers
    [:developer
     [:name "Amar Mehta"]]]
   [:scm
    [:url "https://github.com/xfthhxk/kaiao"]
    [:connection "scm:git:https://github.com/xfthhxk/kaiao.git"]
    [:developerConnection "scm:git:ssh:git@github.com:xfthhxk/kaiao.git"]
    [:tag (str "v" version)]]])

(defn jar [_]
  (clean nil)
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis @*basis*
                :src-dirs clj-src-dirs
                :pom-data (pom-template version)})
  (b/copy-dir {:src-dirs (conj clj-src-dirs "resources")
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file}))

(defn uber
  [{:keys [aliases] :as opts}]
  (prn opts)
  (let [aliases (remove nil? aliases)
        m (cond-> {}
            (seq aliases) (assoc :aliases aliases))]
    (set-basis! m))
  (clean nil)
  (compile-clj nil)
  (b/copy-dir {:src-dirs (conj clj-src-dirs "resources")
               :target-dir class-dir})
  (-> {:main 'kaiao.main
       :manifest {"git-sha" (git-sha)}
       :uber-file uber-jar-file
       :src-dirs clj-src-dirs
       :class-dir class-dir
       :ns-compile '[kaiao.main]
       :basis @*basis*}
      (merge (dissoc opts :aliases))
      b/uber))

(defn install [_]
  (jar nil)
  (println "Installing jar into local Maven repo cache...")
  (b/install {:lib lib
              :version version
              :src-dirs clj-src-dirs}))

(defn deploy [_]
  (dd/deploy {:installer :remote
              :artifact (b/resolve-path jar-file)
              :pom-file (b/pom-path {:lib lib
                                     :class-dir class-dir})}))


(comment
  (set-basis! {:aliases [:gcp]})
  @*basis*
  )
