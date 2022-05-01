(ns arctype.test.resource
  (:require
   [arctype.service.protocol :refer :all]
   [arctype.service.protocol.health :refer [healthy?]]
   [arctype.test.util :refer [eventually*]]
   [clojure.test :refer :all]
   [clojure.tools.logging :as log]
   [sundbry.resource :as resource]))

(def ^:dynamic *test-resource* nil)
(def ^:dynamic *test-system* nil)

(defrecord MockResource [])

(defn mock-resource
  ([resource-name]
   (mock-resource resource-name ->MockResource))
  ([resource-name constructor]
   (resource/make-resource (constructor) resource-name)))

(defn test-system-resource
  ([instance test-fn]
   (test-system-resource
    instance #{} test-fn))
  ([instance deps test-fn]
   (let [system (resource/make-system
                 (->MockResource)
                 :test-system
                 (cons instance deps))
         system (-> system
                    (resource/initialize)
                    (resource/invoke start))]
     (try
       (binding [*test-system* system
                 *test-resource* (resource/require system (resource/name instance))]
         (test-fn))
       (finally
         (resource/invoke-reverse system stop))))))

(defn with-test-resource*
  ([lazy-instance]
   (with-test-resource* lazy-instance (fn [] [])))

  ([lazy-instance lazy-deps]
   (fn [test-fn]
     (test-system-resource (lazy-instance) (lazy-deps) test-fn))))

(defmacro with-test-resource
  ([instance-constructor]
   `(with-test-resource* (fn [] ~instance-constructor)))

  ([instance-constructor deps-constructor]
   `(with-test-resource*
      (fn [] ~instance-constructor)
      (fn [] ~deps-constructor))))

(defn require-test-resource
  "Require a resource in the dynamic context of a test."
  [resource-name]
  (or (resource/acquire *test-resource* resource-name)
      (resource/require *test-system* resource-name)))

(defn test-with-healthy-resource
  "Returns a fixture that will wait for resource-name to become healthy first."
  ([] (test-with-healthy-resource nil))
  ([resource-name]
   (fn [test-fn]
     (let [service (if resource-name
                     (require-test-resource resource-name)
                     *test-resource*)]
       (if (eventually* #(healthy? service) 10000 100)
         (test-fn)
         (do
           (log/error {:message "Resource did not become healthy"
                       :resource resource-name})
           ; Report the resource name as a test failure
           (is (nil? resource-name))))))))