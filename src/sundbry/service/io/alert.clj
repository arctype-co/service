(ns ^{:doc "Alert client"}
  sundbry.service.io.alert
  (:require
    [clojure.tools.logging :as log]
    [schema.core :as S]
    [sundbry.resource :as resource]
    [sundbry.service.protocol :refer :all]))

(def Config
  {(S/optional-key :log?) S/Bool})

(def ^:private sensitive-keys
  #{:password :facebook-access-token "password" "facebook-access-token"})

(defn- sanitize-keys
  [value]
  (cond
    (map? value)
    (->> value
         (map (fn [[k v]]
                (if (contains? sensitive-keys k)
                  [k "********"]
                  [k (sanitize-keys v)])))
         (into {}))
    :else value))

(defn- sanitized-ex-data
  [ex]
  (when-let [exd (ex-data ex)]
    (sanitize-keys exd)))

(defn- serializable-exception
  "Return an exception that can be serialized. Sometimes, our ex-data is unserializable."
  [^Throwable ex]
  (when (some? ex)
    (let [exd (sanitized-ex-data ex)]
      (cond
        (= :schema.core/error (:type exd))
        (proxy [clojure.lang.ExceptionInfo]
          [(.getMessage ex)
           (dissoc exd :schema :value)
           (serializable-exception (.getCause ex))]

          (getStackTrace []
            (.getStackTrace ex)))

        :else ex))))

(defrecord AlertManager [config]

  PAlertHandler
  (exception [this ex]
    (when (:log? config)
      (log/error (serializable-exception ex)
                 {:message "Alert!"
                  :ex-message (sanitize-keys (.getMessage ex))}))
    nil)

  (ring-exception [this ex request]
    (when (:log? config)
      (log/error (serializable-exception ex)
                 {:message "Alert!"
                  :ex-message (sanitize-keys (.getMessage ex))
                  :request (sanitize-keys request)}))
    nil))

(S/defn create
  [resource-name
   config :- Config]
  (resource/make-resource
    (map->AlertManager
      {:config config})
    resource-name))
