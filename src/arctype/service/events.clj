(ns arctype.service.events
  (:require
    [schema.core :as S]
    [sundbry.resource :as resource]
    [arctype.service.protocol :refer :all]))

(def Config
  (S/maybe {}))

(def Schemas
  {S/Keyword {S/Keyword S/Any}}) ; {topic -> {type -> schema}}

(def ^:private default-config {})

(defn- compile-topic-schemas
  [schemas]
  (->> schemas
       (map (fn [[topic schemas]]
              [topic 
               (->> schemas
                    (map (fn [[event-type data-schema]]
                           [event-type (S/validator data-schema)]))  
                    (into {}))]))
       (into {})))

(defn- validate-data
  [{:keys [compiled-topic-schemas]} topic event-type data]
  (let [schemas (get compiled-topic-schemas topic)
        validator (get schemas event-type)]
    (when (nil? validator)
      (throw (ex-info "Undefined event type"
                      {:topic topic
                       :event-type event-type})))
    (validator data)))

(defn- wrap-event
  "Wrap an event by type and check it's schema."
  [this topic event-type data]
  {:event event-type
   :timestamp (System/currentTimeMillis)
   :data (validate-data this topic event-type data)})

(defn raise!
  [this topic event-type data]
  (let [driver (resource/require this (:driver-name this))]
    (put-event! driver topic (wrap-event this topic event-type data))))

(defn start-consumer
  [this topic options handler]
  {})

(defn stop-consumer
  [consumer]
  nil)

(S/defn create
  [resource-name
   config :- Config
   schemas :- Schemas
   driver-name]
  (let [config (merge default-config config)]
    (resource/make-resource
      {:config config
       :driver-name driver-name
       :compiled-topic-schemas (compile-topic-schemas schemas)}
      resource-name
      #{driver-name})))
