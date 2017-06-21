(ns arctype.service.util
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
                     (ex-info "Coercion error" result)
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
                     (ex-info "Coercion error" result)
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
