(ns arctype.service.io.durable-events-test
  (:import
    [java.nio.file Files]
    [java.nio.file.attribute FileAttribute])
  (:require
    [clojure.test :refer :all]
    [arctype.service.util :refer [recursive-delete]]
    [arctype.service.io.durable-events :as events]))

(def ^:private ^:dynamic *queues-path*)

(defn- fix-queues-path
  [test-fn]
  (let [path-obj (Files/createTempDirectory nil (make-array FileAttribute 0))]
    (binding [*queues-path* (str path-obj)]
      (test-fn))
    (recursive-delete path-obj)))

(use-fixtures :each fix-queues-path)

(deftest test-create-events
  (is (some? (events/create :events
                            {:queues-path *queues-path*
                             :queues-options {}}
                            {}))))
