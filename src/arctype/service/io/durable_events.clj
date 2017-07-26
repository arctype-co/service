(ns arctype.service.io.durable-events
  (:require
    [clojure.core.async :as async]
    [clojure.tools.logging :as log]
    [durable-queue :as Q]
    [schema.core :as S]
    [sundbry.resource :as resource]))

(def Config
  {:queues-path S/Str ; directory name to read/write queues
   :queues-options {S/Keyword S/Any}})

(def Schemas
  {S/Keyword {S/Keyword S/Any}}) ; {topic -> {type -> schema}}

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

(S/defn raise!
  [{:keys [queues] :as this} topic event-type data]
  (Q/put! queues topic (wrap-event this topic event-type data)))

(defn- start-handler-thread
  [{:keys [queues]} input handler]
  (async/thread
    (loop []
      (when-let [task (async/<!! input)]
        (try 
          (let [event (deref task)]
            (handler event))
          (Q/complete! task)
          (catch Exception e
            (log/error e {:message "Event handler failed"})
            (Q/retry! task)))
        (recur)))))

(S/defn start-consumer
  [{:keys [queues] :as this} topic buffer-size handler]
  (let [input (async/chan (async/buffer buffer-size))
        reader-thread (doto (Thread.
                              (fn []
                                (try 
                                  (loop []
                                    (async/>!! input (Q/take! queues topic))
                                    (recur))
                                  (catch InterruptedException e
                                    (log/debug {:message "Event reader thread stopped."}))
                                  (catch Exception e
                                    (log/fatal e {:message "Event reader thread failed!"}))
                                  (finally 
                                    (async/close! input)))))
                        (.start))
        handler-thread (start-handler-thread this input handler)]
    {:reader-thread reader-thread
     :handler-thread handler-thread
     :input input}))

(S/defn stop-consumer
  [{:keys [reader-thread handler-thread]}]
  (.interrupt reader-thread)
  (async/<!! handler-thread))

(S/defn create
  [resource-name 
   config :- Config 
   schemas :- Schemas]
  (resource/make-resource
    {:queues (Q/queues (:queues-path config) (:queues-options config))
     :compiled-topic-schemas (compile-topic-schemas schemas)}
    resource-name))
