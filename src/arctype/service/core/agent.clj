(ns arctype.service.core.agent
  (:require
   [clojure.core.async :as async]
   [clojure.tools.logging :as log]
   [schema.core :as S]))

;; Generic agent that constructs and destroys itself with a retry mechanism.

(defn agent-online?
  [pulsar-agent]
  (some? (:instance @pulsar-agent)))

(def AgentConfig
  {(S/optional-key :agent-retry-ms) S/Int
   (S/optional-key :stop-timeout-ms) S/Int
   (S/optional-key :pulsar-resource-name) S/Keyword
   S/Keyword S/Any})

(def default-agent-config
  {:agent-retry-ms 5000
   :stop-timeout-ms 10000
   :pulsar-resource-name :pulsar})

(defn agent-config
  [obj]
  (select-keys obj [:agent-retry-ms :stop-timeout-ms]))

(defn- agent-error-handler
  [failing-agent ex]
  (log/error ex {:message "gRPC client agent failure!"}))

(defn create-async-agent
  []
  (let [new-agent (agent {})]
    (set-error-handler! new-agent agent-error-handler)
    (set-error-mode! new-agent :fail)
    new-agent))

(defn agent-instance
  [async-agent]
  (:instance @async-agent))

(defn agent-instance-with-epoch
  [async-agent]
  (let [agent-state @async-agent]
    [(:instance agent-state) (:epoch agent-state)]))

(S/defn start-async-agent!
  ; Restart/ stop agent arity
  ([config async-agent] (start-async-agent! config async-agent nil nil))

  ; Initial startup
  ([{:keys [agent-retry-ms] :as config} :- AgentConfig
    async-agent
    create-instance
    destroy-instance]
   (send-off async-agent
             (fn [{:keys [instance restart-epoch stop constructor destructor epoch] :as agent-state}]
               (if stop
                 (do
                   (try
                     (when instance
                       (destructor instance))
                     (catch Exception e
                       (log/warn e {:message "Failed to destroy instance"})))
                   (-> agent-state
                       (dissoc :instance)
                       (assoc :stop true)))
                 (let [constructor (or constructor create-instance)
                       destructor (or destructor destroy-instance)
                       epoch (or epoch 0)
                       agent-state (assoc agent-state
                                          :constructor constructor
                                          :destructor destructor
                                          :epoch epoch)]
                   (try
                     (let [instance* (if (and (= restart-epoch epoch)
                                              (some? instance))
                                       (do
                                         (destructor instance)
                                         nil)
                                       instance)]
                       (assoc agent-state
                              :instance (or instance* (constructor))
                              :restart-epoch restart-epoch
                              :stop false
                              :epoch (if instance* epoch (inc epoch))))
                     (catch Exception e
                       (log/warn e {:message "Failed to create instance"})
                       (async/go
                         (async/<! (async/timeout agent-retry-ms))
                         (start-async-agent! config async-agent))
                       agent-state))))))
   nil))

(S/defn stop-async-agent!
  [{stop-timeout-ms :stop-timeout-ms :as config} :- AgentConfig
   async-agent]
  (send async-agent assoc :stop true)
  ; Call the start function, which will shutdown if :stop is set
  (start-async-agent! config async-agent)
  (await-for stop-timeout-ms async-agent)
  nil)

(defn restart-async-agent!
  "Restarts the agent instance at epoch."
  [config async-agent epoch]
  (log/debug {:message "Restarting async agent"
              :epoch epoch})
  (send async-agent assoc :restart-epoch epoch)
  (start-async-agent! config async-agent)
  nil)