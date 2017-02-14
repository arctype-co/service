(ns sundbry.service.test-config
  (:require
    [clojure.test :refer :all]
    [sundbry.service.config :as config]))

(deftest test-read-config
  (is (some? (config/read "resources/sample.yml"))))
