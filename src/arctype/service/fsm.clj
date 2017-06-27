(ns arctype.service.fsm
  (:require
    [clojure.core.async :as async :refer [>!!]]
    [clojure.tools.logging :as log]
    [arctype.service.protocol :refer :all]
    [arctype.service.util :refer [thread-try <??]]
    [schema.core :as S]))

(def FsmSpec
  {:states {S/Keyword S/Any} ; Keyword -> (fn [client])
   :transitions [[(S/one S/Keyword :current-state)
                  (S/one S/Keyword :transition)
                  (S/one S/Keyword :next-state)]]
   :initial-state S/Keyword
   :terminal-states #{S/Keyword}})

(def default-config
  {:stop-timeout-ms 30000})

(def Config
  {:client S/Any
   :spec FsmSpec
   (S/optional-key :stop-timeout-ms) S/Int})

(defn- create-events
  [this]
  (let [ch (async/chan (async/buffer 32))]
    (async/put! ch [::begin])
    ch))

(defn- destroy-events
  [this]
  (async/close! (:events this))
  nil)

(defn- transition-state
  "Return the new state, or nil if no valid transition."
  [{:keys [compiled-transitions initial-state]} current-state transition]
  (if (= ::begin transition)
    initial-state
    (get compiled-transitions [current-state transition])))

(defn- enter-state
  "Apply state entry"
  [{{states :states} :spec
    client :client
    :as this}
   new-state
   transition-args]
  (if-let [state-fn (get states new-state)]
    (do
      (apply state-fn this client transition-args)
      new-state)
    (throw (ex-info "Undefined FSM state entered!"
                    {:state new-state}))))

(defn- create-thread
  [{:keys [events spec state] :as this}]
  (thread-try
    (loop []
      (when-let [[transition & transition-args] (<?? events)]
        (when (not= ::terminate transition)
          (let [new-state 
                (try
                  (swap! state
                         (fn [cur-state]
                           (if-let [next-state (transition-state spec cur-state transition)]
                             (enter-state this next-state transition-args)
                             (do
                               (log/debug {:message "Invalid transition"
                                           :current-state cur-state
                                           :transition transition
                                           :transition-args transition-args})
                               cur-state))))
                  (catch Exception e
                    (log/error e {:message "FSM state transition failed!"})))]
            (if (contains? (:terminal-states spec) new-state)
              (log/debug {:message "FSM reached terminal state."
                          :state new-state})
              (recur))))))
    (log/debug {:message "FSM thread terminated."})))

(defn- destroy-thread
  [{:keys [stop-timeout-ms thread events] :as this}]
  ; Wait for graceful stop
  (let [[_ end] (async/alts!! [thread (async/timeout stop-timeout-ms)])]
    (when (not= thread end) ; timeout
      (log/warn {:message "FSM termination timed out."
                 :stop-timeout-ms stop-timeout-ms})
      ; Ensure thread termination
      (async/put! events [::terminate])))
  nil)

(defn no-op
  "State function to do nothing"
  [fsm client & args]
  nil)

(defn state
  "Return current state."
  [this]
  (deref (:state this)))

(defn transition!
  [{:keys [events]} transition & args]
  (async/put! events (into [transition] args)))

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
        (dissoc this :state)
        (do
          (log/debug {:message "FSM stopped"})
          this))))

(defmethod print-method FiniteStateMachine [{:keys [state] :as this} writer]
  (let [cur-state (when (some? state) @state)]
    (print-simple (str "FiniteStateMachine[state=" cur-state "]#" (hash this)) writer)))

(defn- compile-transitions 
  [{:keys [transitions] :as spec}]
  (assoc spec :compiled-transitions
         (into {}
               (for [[from-state transition to-state] transitions]
                 {[from-state transition] to-state}))))

(S/defn create
  [config :- Config]
  (let [config (merge default-config config)]
    (map->FiniteStateMachine
      (update config :spec compile-transitions))))
