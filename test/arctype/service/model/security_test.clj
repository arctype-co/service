(ns arctype.service.model.security-test
  (:require
    [clojure.test :refer :all]
    [arctype.service.model.security :as security]))

(deftest test-secure-id
  (is (string? (security/secure-id))))
