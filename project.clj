(defproject shandor "0.1.0-SNAPSHOT"
  :description "Gives your emails a shelf life and deletes them when they're expired."
  :url "https://github.com/rmoehn/shandor"
  :license {:name "MIT License"
            :url "http://opensource.org/licenses/MIT"}

  :source-paths ["src/clj"]
  :javac-options ["-target" "1.8" "-source" "1.8"]
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [net.java.dev.jna/jna "4.0.0"]
                 [net.n01se/clojure-jna "1.0.0"]
                 [clojure.joda-time "0.2.0"]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 [org.clojure/core.logic "0.8.10"]
                 [prismatic/plumbing "0.5.0"]]
  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.7"]]
                   :source-paths ["dev"]}
             :uberjar {:main shandor.core
                       :aot :all}}

  :jar-name "shandor-%s-slim.jar"
  :uberjar-name "shandor-%s.jar")
