(ns arctype.service.test-config
  (:require
    [clojure.test :refer :all]
    [arctype.service.config :as config]))

(deftest test-read-config
  (let [cfg (config/read "resources/sample.yml")]
    (is (some? cfg))
    (doseq [k (keys cfg)]
      (is (keyword? k)))))
