(ns arctype.service.util
  #?(:clj
      (:import [java.nio.file Files FileVisitor FileVisitResult]))
  #?(:clj
      (:require
        [clojure.core.async :as async]
        [clojure.tools.logging :as log]
        [schema.core :as S]
        [schema.coerce :as coerce]
        [schema.utils :as schema-utils]))
  #?(:cljs
      (:require
        [cljs.core.async :as async]
        [schema.core :as S])))

(defn map-keys
  [func dict]
  (into {} (map (fn [[k v]] [(func k) v]) dict)))

(defn map-vals
  [func dict]
  (into {} (map (fn [[k v]] [k (func v)]) dict)))

(defn rmerge [left right]
  (if (map? left)
    (if (map? right)
      (merge-with rmerge left right)
      (throw #?(:clj (Exception. "Map structure mismatch")
                     :cljs (js/Error "Map structure mismatch"))))
    (if (some? right) right left)))

#?(:clj
    (defn error?
      [obj]
      (instance? Throwable obj)))

#?(:clj
    (defn throw-err
      "Throw element if it is Throwable, otherwise return it."
      [element]
      (when (instance? Throwable element)
        (throw element))
      element))

#?(:cljs
     (defn throw-err
      "Throw element if it is an Error, otherwise return it"
      [element]
      (when (instance? js/Error element)
        (throw element))
      element))

#?(:clj
    (defmacro <?
      "Like core.async/<! but throws if the message is Throwable"
      [ch]
      `(throw-err (async/<! ~ch))))

#?(:clj
    (defn <??
      "Like core.async/<!! but throws if the message is Throwable"
      [ch]
      (throw-err (async/<!! ch))))

#?(:clj
    (defmacro go-try
      "A core.async/go block, with an implicit try...catch. Exceptions are
      returned (put onto the go block's result channel)."
      [& body]
      `(async/go
         (try
           (do ~@body)
           (catch Throwable t#
             (log/error t# "Exception in async go block")
             t#)))))

#?(:clj
    (defmacro thread-try
      "Similar to go-try but uses thread instead of go."
      [& body]
      `(async/thread
         (try
           (do ~@body)
           (catch Throwable t#
             (log/error t# "Exception in async thread block")
             t#)))))

#?(:clj
    (defn xform-validator
      "Returns a transducer to validate schema-def"
      [schema-def]
      (let [validate (S/validator schema-def)]
        (map (fn [result]
               (if (instance? Throwable result)
                 result
                 (try (validate result)
                      (catch Exception e
                        e))))))))

#?(:clj
    (defn xform-json-coercer
      "Returns a transducer to coerce schema-def from JSON"
      [schema-def]
      (let [coerce (coerce/coercer schema-def coerce/json-coercion-matcher)]
        (map (fn [result]
               (if (instance? Throwable result)
                 result
                 (let [result (coerce result)]
                   (if (schema-utils/error? result)
                     (ex-info "Coercion error" (into {} result))
                     result))))))))

#?(:clj
    (defn xform-string-coercer
      "Returns a transducer to coerce schema-def from string values"
      [schema-def]
      (let [coerce (coerce/coercer schema-def coerce/string-coercion-matcher)]
        (map (fn [result]
               (if (instance? Throwable result)
                 result
                 (let [result (coerce result)]
                   (if (schema-utils/error? result)
                     (ex-info "Coercion error" (into {} result))
                     result))))))))

#?(:clj 
    (defmacro maybe
      [& body]
      `(try 
         (do ~@body)
         (catch Exception e#
           (log/debug {:message "Maybe not"
                       :error e#})
           nil))))

#?(:clj 
    (defmacro possibly
      [& body]
      `(try 
         (do ~@body)
         (catch Exception e#
           e#))))

#?(:clj
    (defn recursive-delete
      [top-path]
      (let [visitor (proxy [FileVisitor] []
                      (postVisitDirectory [path ex]
                        (Files/delete path)
                        FileVisitResult/CONTINUE)
                      (preVisitDirectory [path ex]
                        FileVisitResult/CONTINUE)
                      (visitFile [path ex]
                        (Files/delete path)
                        FileVisitResult/CONTINUE)
                      (visitFileFailed [path ex]
                        FileVisitResult/CONTINUE))]
        (Files/walkFileTree top-path visitor))))

(defn redact
  [dict path]
  (if (some? (get-in dict path))
    (assoc-in dict path "********")
    dict))

(defn simple-keys
  "Filters out entries with prefix keyword-ns
   in namespaced-dict"
  [namespaced-dict keyword-ns]
  (let [ns-name (name keyword-ns)]
    (reduce-kv
      (fn [dict k v]
        (if (= ns-name (namespace k))
          (assoc dict (keyword (name k)) v)
          dict))
      {}
      namespaced-dict)))
