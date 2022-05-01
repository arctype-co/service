(defproject arctype/service "1.2.0-SNAPSHOT" 
  :dependencies
  [[org.clojure/clojure "1.10.1"]
   [org.clojure/tools.logging "1.1.0"]
   [commons-codec/commons-codec "1.15"]
   [io.forward/yaml "1.0.10"]
   ; Requires branch with S/defprotocol support
   ; https://github.com/plumatic/schema/pull/432
   [prismatic/schema "1.2.1-SNAPSHOT"]
   [sundbry/resource "0.4.0"]]

  :source-paths ["src"]

  :test-selectors
  {:default #(not (:skip %))
   :unit #(and (not (:skip %)) (:unit %)) })
