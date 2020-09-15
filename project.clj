(defproject arctype/service "1.1.0-SNAPSHOT" 
  :dependencies
  [[org.clojure/clojure "1.10.1"]
   [org.clojure/core.async "0.4.490"]
   [org.clojure/tools.logging "0.3.1"]
   [commons-codec/commons-codec "1.10"]
   [crypto-password "0.1.3"
    :exclusions [commons-codec]]
   [io.forward/yaml "1.0.9"
    :exclusions [org.flatland/ordered]]
   [org.flatland/ordered "1.5.7"]
   [http-kit "2.4.0-alpha3"]
   [prismatic/schema "1.1.10"]
   [sundbry/resource "0.4.0"]
   [throttler "1.0.0"
    :exclusions [org.clojure/clojure
                 org.clojure/core.async]]]

  :source-paths ["src"]

  :test-selectors
  {:default #(not (:skip %))
   :unit #(and (not (:skip %)) (:unit %)) }

  :jvm-opts ["-server"])
