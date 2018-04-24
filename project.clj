(defproject arctype/service "0.1.0-SNAPSHOT" 
  :dependencies
  [[org.clojure/clojure "1.8.0"]
   [org.clojure/core.async "0.2.374"]
   [org.clojure/core.match "0.3.0-alpha4"]
   [org.clojure/java.jdbc "0.7.0-alpha1"]
   [org.clojure/tools.cli "0.3.3"]
   [org.clojure/tools.namespace "0.2.10"]
   [org.clojure/tools.nrepl "0.2.12"]
   [org.clojure/tools.logging "0.3.1"]
   [org.xerial/sqlite-jdbc "3.16.1"]
   [com.mchange/c3p0 "0.9.5.2"] ; postgres
   [cheshire "5.6.1"]
   [commons-codec/commons-codec "1.10"]
   [crypto-password "0.1.3"
    :exclusions [commons-codec]]
   [factual/durable-queue "0.1.7-SNAPSHOT"] ;durable-events
   [io.forward/yaml "1.0.5"]
   [http-kit "2.3.0-alpha2"]
   [log4j/log4j "1.2.17"]
   [org.im4java/im4java "1.4.0"] ; im
   [org.postgresql/postgresql "9.4.1208"] ; postgres
   [prismatic/schema "1.1.7"]
   [sundbry/resource "0.4.0"]
   [throttler "1.0.0"
    :exclusions [org.clojure/clojure
                 org.clojure/core.async]]]

  :source-paths ["src"]
  :java-source-paths ["java-src"]

  :test-selectors
  {:default #(not (:skip %))
   :unit #(and (not (:skip %)) (:unit %)) }

  :jvm-opts ["-server"])
