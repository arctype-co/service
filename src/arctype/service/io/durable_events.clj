(ns arctype.service.io.durable-events
  (:require
    [clojure.core.async :as async]
    [clojure.tools.logging :as log]
    [durable-queue :as Q]
    [schema.core :as S]
    [sundbry.resource :as resource]))

(def ^:private default-config
  {:reset-corrupt? true})

(def Config
  {:queues-path S/Str ; directory name to read/write queues
   :queues-options {S/Keyword S/Any}
   (S/optional-key :reset-corrupt?) S/Bool ; Delete the queues when data is corrupted. If false, queue thread will abort on corrupt data.
   })

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

(defn sync!
  [{:keys [queues]}]
  (Q/fsync queues))

(defn- start-handler-thread
  [{:keys [queues]} input handler]
  (async/thread
    (loop []
      (when-let [task (async/<!! input)]
        (try 
          (let [event (:data task)]
            (handler event))
          (Q/complete! task)
          (catch Exception e
            (log/error e {:message "Event handler failed"})
            (Q/retry! task)))
        (recur)))))

(defn- read-task-data
  [task]
  (assoc task :data @task))

(defn- safe-take!
  [{:keys [config queues] :as this} topic]
  (if (:reset-corrupt? config)
    (if-let [data (try 
                    (read-task-data (Q/take! queues topic))
                    (catch java.io.IOException io-error
                      (log/error io-error {:message "Corrupt queue data. Resetting queue files. Data may be lost."
                                           :exception-message (.getMessage io-error)
                                           :topic topic})
                      nil))]
      data
      (do
        ; Hazard: this will delete ALL topics
        (Q/delete! queues)
        (recur this topic)))
    (read-task-data (Q/take! queues topic))))

(S/defn start-consumer
  [this topic buffer-size handler]
  (let [input (async/chan (async/buffer buffer-size))
        reader-thread (doto (Thread.
                              (fn []
                                (try 
                                  (loop []
                                    (let [task (safe-take! this topic)]
                                      (async/>!! input task))
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
  (let [config (merge default-config config)]
    (resource/make-resource
      {:queues (Q/queues (:queues-path config) (:queues-options config))
       :compiled-topic-schemas (compile-topic-schemas schemas)
       :config config}
      resource-name)))
