(ns sundbry.service.util
  #?(:clj
      (:require
        [clojure.core.async :as async]
        [clojure.tools.logging :as log]
        [schema.core :as S]))
  #?(:cljs
      (:require
        [cljs.core.async :as async]
        [schema.core :as S])))

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
  [schema-def handle-error]
  (let [validate (S/validator schema-def)]
    (map (fn [result]
           (if (instance? Throwable result)
             result
             (try (validate result)
              (catch Exception e
               (if (some? handle-error)
                  (handle-error e)
                  e)))))))))
