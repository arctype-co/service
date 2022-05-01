(defproject co.arctype/service "1.2.1-SNAPSHOT"
  :dependencies
  [[org.clojure/clojure "1.11.1"]
   [org.clojure/tools.logging "1.2.4"]
   [commons-codec/commons-codec "1.15"]
   [io.forward/yaml "1.0.11"]
   ; Requires branch with S/defprotocol support
   ; https://github.com/plumatic/schema/pull/432
   [co.arctype/schema "1.2.1-defprotocol"]
   [sundbry/resource "0.4.0"]]

  :source-paths ["src"]

  :test-selectors
  {:default #(not (:skip %))
   :unit #(and (not (:skip %)) (:unit %)) })
