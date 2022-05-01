(ns arctype.test.util
  (:require
   [schema.core :as S]))

(defn eventually*
  "Return true if boolean-fn evaluates to true within timeout-ms"
  [boolean-fn timeout-ms retry-delay-ms]
  (loop [start-ts (System/currentTimeMillis)
         now nil]
    (if (> (or now start-ts)
           (+ start-ts timeout-ms))
      false
      (if (boolean-fn)
        true
        (do
          (when-not (zero? retry-delay-ms)
            (Thread/sleep retry-delay-ms))
          (recur start-ts (System/currentTimeMillis)))))))

(defn with-schema-validation
  [test-fn]
  (S/set-fn-validation! true)
  (test-fn))