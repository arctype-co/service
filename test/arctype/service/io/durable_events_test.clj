(ns arctype.service.io.durable-events-test
  (:import
    [java.nio.file Files]
    [java.nio.file.attribute FileAttribute])
  (:require
    [clojure.core.async :as async]
    [clojure.test :refer :all]
    [clojure.tools.logging :as log]
    [schema.core :as S]
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

(deftest test-rw-events
  (let [instance (events/create :events
                                {:queues-path *queues-path*
                                 :queues-options {}}
                                {:foo {:bar S/Any}})
        event-count 100]
    (is (some? instance))
    (dotimes [n event-count]
      (events/raise! instance :foo :bar {:message "hello" :n n}))
    (events/sync! instance)
    (let [counter (atom 0)
          consumer (events/start-consumer instance :foo 10 (fn [evt] (swap! counter inc)))]
      (Thread/sleep 500)
      (events/stop-consumer consumer)
      (is (= event-count @counter)))))

(deftest test-rw-parallelism
  (let [instance (events/create :events
                                {:queues-path *queues-path*
                                 :queues-options {}}
                                {:foo {:bar S/Any}})
        event-count 100
        run-test? (atom true)
        w-thread (async/thread
                   (loop [n 0]
                     (when @run-test?
                       (events/raise! instance :foo :bar {:message "hello" :n n}) 
                       (Thread/sleep 10)
                       (recur (inc n)))))
        rd-counter (atom 0)
        consumer (events/start-consumer instance :foo 10 (fn [evt] (swap! rd-counter inc)))]
    (Thread/sleep 500)
    (reset! run-test? false)
    (events/stop-consumer consumer)
    (is (< 0 @rd-counter))))
