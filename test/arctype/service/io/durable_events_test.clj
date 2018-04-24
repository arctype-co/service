(ns arctype.service.io.durable-events-test
  (:import
    [java.io RandomAccessFile File]
    [java.nio.file Files FileVisitor FileVisitResult]
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
    (binding [*queues-path* path-obj]
      (test-fn))
    (recursive-delete path-obj)))

(use-fixtures :each fix-queues-path)

(deftest test-create-events
  (is (some? (events/create :events
                            {:queues-path (str *queues-path*)
                             :queues-options {}}
                            {}))))

(deftest test-rw-events
  (let [instance (events/create :events
                                {:queues-path (str *queues-path*)
                                 :queues-options {}}
                                {:foo {:bar S/Any}})
        event-count 100]
    (is (some? instance))
    (dotimes [n event-count]
      (events/raise! instance :foo :bar {:message "hello" :n n}))
    (events/sync! instance)
    (let [counter (atom 0)
          consumer (events/start-consumer instance :foo 1 (fn [evt] (swap! counter inc)))]
      (Thread/sleep 500)
      (events/stop-consumer consumer)
      (is (= event-count @counter)))))

(deftest test-no-events
  (let [instance (events/create :events
                                {:queues-path (str *queues-path*)
                                 :queues-options {}}
                                {:foo {:bar S/Any}})]
    (is (some? instance))
    (let [counter (atom 0)
          consumer (events/start-consumer instance :foo 1 (fn [evt] (swap! counter inc)))]
      (Thread/sleep 500)
      (events/stop-consumer consumer)
      (is (= 0 @counter)))))

(deftest test-rw-parallelism
  (let [instance (events/create :events
                                {:queues-path (str *queues-path*)
                                 :queues-options {}}
                                {:a {:bar S/Any}
                                 :b {:bar S/Any}})
        event-count 100
        run-test? (atom true)
        w-thread (async/thread
                   (loop [n 0]
                     (when @run-test?
                       (events/raise! instance 
                                      (if (even? n) :a :b)
                                      :bar {:message "hello" :n n}) 
                       (Thread/sleep 10)
                       (recur (inc n)))))
        rd-counter (atom 0)
        consumer-a (events/start-consumer instance :a 1 (fn [evt] (swap! rd-counter inc)))
        consumer-b (events/start-consumer instance :b 1 (fn [evt] (swap! rd-counter inc)))]
    (Thread/sleep 500)
    (reset! run-test? false)
    (events/stop-consumer consumer-a)
    (events/stop-consumer consumer-b)
    (is (< 0 @rd-counter))))

(defn- corrupt-data-path
  [path]
  (let [raf (RandomAccessFile. (.toFile path) "rw")]
    (log/debug {:message "Corrupting queue file data"
                :path (str path)
                :size (.length raf)})
    (.setLength raf 1488)
    (.close raf)))

(deftest test-rw-parallelism-corrupt
  (let [instance (events/create :events
                                {:queues-path (str *queues-path*)
                                 :queues-options {}}
                                {:a {:bar S/Any}
                                 :b {:bar S/Any}})
        event-count 500]
    (is (some? instance))
    (dotimes [n event-count]
      (events/raise! instance (if (even? n) :a :b) :bar {:message "hello" :n n}))
    (events/sync! instance)
  ; Corrupt the data in queues path
  (let [visitor (proxy [FileVisitor] []
                  (postVisitDirectory [path ex]
                    FileVisitResult/CONTINUE)
                  (preVisitDirectory [path ex]
                    FileVisitResult/CONTINUE)
                  (visitFile [path ex]
                    (corrupt-data-path path)
                    FileVisitResult/CONTINUE)
                  (visitFileFailed [path ex]
                    FileVisitResult/CONTINUE))]
    (Files/walkFileTree *queues-path* visitor))
  (let [run-test? (atom true)
        #_w-thread #_(async/thread
                   (loop [n 0]
                     (when @run-test?
                       (events/raise! instance 
                                      (if (even? n) :a :b)
                                      :bar {:message "hello" :n n}) 
                       (Thread/sleep 10)
                       (recur (inc n)))))
        rd-counter (atom 0)
        consumer-a (events/start-consumer instance :a 1 (fn [evt] (swap! rd-counter inc)))
        consumer-b (events/start-consumer instance :b 1 (fn [evt] (swap! rd-counter inc)))]
    (Thread/sleep 5000)
    (reset! run-test? false)
    (events/stop-consumer consumer-a)
    (events/stop-consumer consumer-b)
    (is (< 0 @rd-counter)))))
