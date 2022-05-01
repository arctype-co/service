(ns arctype.mock.protocol
  (:import
   [java.nio ByteBuffer])
  (:require
   [taoensso.nippy :as nippy]))

(defprotocol PMockIntrospection
  (received? [this method-name args]))

(defn wrap-args
  [args]
  ; Provides binary equality
  (ByteBuffer/wrap (nippy/freeze args)))

(defn mock-call
  [mock-state method-name args]
  (swap! mock-state update-in [method-name (wrap-args args)]
         (fn [call-count]
           (inc (or call-count 0)))))

(defn mock-received?
  [mock-state method-name args]
  (if (= :any args)
    (contains? @mock-state method-name)
    (some? (get-in @mock-state [method-name (wrap-args args)]))))