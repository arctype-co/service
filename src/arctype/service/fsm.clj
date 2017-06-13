(ns arctype.service.fsm
  (:require
    [clojure.core.async :as async :refer [>!!]]
    [clojure.tools.logging :as log]
    [arctype.service.protocol :refer :all]
    [arctype.service.util :refer [thread-try <??]]
    [schema.core :as S]))

(def FsmSpec
  {:states {S/Keyword S/Any} ; Keyword -> (fn [client])
   :transitions [(S/one S/Keyword :current-state)
                 (S/one S/Keyword :transition)
                 (S/one S/Keyword :next-state)]
   :initial-state S/Keyword})

(def Config
  {:client S/Any
   :spec FsmSpec})

(defn- create-events
  [this]
  (let [ch (async/chan (async/buffer 32))]
    (async/put! ch ::begin)
    ch))

(defn- destroy-events
  [this]
  (async/close! (:events this))
  nil)

(defn- transition-state
  "Return the new state, or nil if no valid transition."
  [{:keys [compiled-transitions]} current-state transition]
  (get compiled-transitions [current-state transition]))

(defn- enter-state
  "Apply state entry"
  [{{states :states} :spec
    client :client} new-state]
  (if-let [state-fn (get states new-state)]
    (do
      (state-fn client)
      new-state)
    (throw (ex-info "Undefined FSM state entered!"
                    {:state new-state}))))

(defn- create-thread
  [{:keys [events spec state] :as this}]
  (thread-try
    (loop []
      (when-let [[transition & args] (<?? events)]
        (when (not= ::terminate transition)
          (swap! state
                 (fn [cur-state]
                   (if-let [next-state (transition-state spec cur-state transition)]
                     (enter-state this next-state)
                     (do
                       (log/debug {:message "Invalid transition"
                                   :current-state cur-state
                                   :transition transition})
                       cur-state))))
          (recur))))))

(defn- destroy-thread
  [this]
  (async/put! (:events this) ::terminate)
  (<?? (:thread this))
  nil)

(defrecord FiniteStateMachine [spec client state events thread compiled-transitions]
  PLifecycle
  (start [this]
    (log/info {:message "Starting finite state machine"})
    (as-> this this
      (assoc this :state (atom nil))
      (assoc this :events (create-events this))
      (assoc this :thread (create-thread this))))

  (stop [this]
    (log/info {:message "Stopping finite state machine"})
    (as-> this this
        (assoc this :thread (destroy-thread this))
        (assoc this :events (destroy-events this))
        (assoc this :state nil))))



(S/defn ^:private compile-transitions :- {[S/Keyword] S/Keyword} ; {[current-state transition] new-state}
  [transition-spec]
  (into {}
        (for [[from-state transition to-state]]
          {[from-state transition] to-state})))

(S/defn create
  [config :- Config]
  (map->FiniteStateMachine
    (assoc config
           :compiled-transitions (compile-transitions (:transitions (:spec config))))))
