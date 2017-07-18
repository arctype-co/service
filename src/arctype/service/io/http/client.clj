(ns ^{:doc "Dedicated HTTP client"}
  arctype.service.io.http.client
  (:require
    [clojure.core.async :as async :refer [<!! >!!]]
    [clojure.java.io :as io]
    [clojure.tools.logging :as log]
    [org.httpkit.client :as http]
    [schema.core :as S]
    [sundbry.resource :as resource]
    [throttler.core :as throttler]
    [arctype.service.util :refer [thread-try]]
    [arctype.service.protocol :refer :all]
    [arctype.service.core :as sys-core])
  (:import
    [org.httpkit.client HttpClient]))

(def ThrottleConfig
  {:rate S/Int
   :period (S/enum :millisecond :second :minute :hour :day #_:month)
   :burst S/Int})

(def Config
  {(S/optional-key :connections) S/Int ; size of connection pool
   (S/optional-key :msg-queue-size) S/Int ; # of messages to buffer
   (S/optional-key :retry-limit) S/Int ; # Max # of retries for a single msg
   (S/optional-key :throttle) ThrottleConfig
   (S/optional-key :proxy-url) S/Str})

(def ^:private default-config
  {:connections 2
   :msg-queue-size 16
   :retry-limit 0})

(defn- handle-response
  [{:keys [result] :as request} response retry-count retry-limit]
  (if-let [error (:error response)]
    (if (< retry-count retry-limit)
      (do
        (log/warn error {:message "HTTP request failed, retrying."
                         :request (dissoc request :result)})
        request)
      (when (some? result)
        ; return the error and move on
        (>!! result error)
        (async/close! result)
        nil))
    ; result channels should have 1 free buffer space so our puts don't block here
    (when (some? result)
      (>!! result response)
      (async/close! result)
      nil)))

(defn- do-request
  [this
   {:keys [result] :as request}]
  (let [http-request (dissoc request :client :result)
        ts (System/currentTimeMillis)
        response (-> request http/request deref)
        response-data (dissoc response :opts)
        elapsed-ms (- (System/currentTimeMillis) ts)]
    (log/trace {:message "HTTP request"
                :request http-request
                :response response-data
                :ms elapsed-ms})
    response))

(defn- create-throttle
  [{:keys [rate period burst] :as throttle-cfg} msg-queue]
  (if (some? throttle-cfg)
    (throttler/throttle-chan msg-queue rate period burst)
    msg-queue))

(defn- worker-thread
  [{:keys [msg-queue config] :as this} thread-num]
  (thread-try
    (let [{:keys [retry-limit]} config
          client (HttpClient.)
          msg-throttle (create-throttle (:throttle config) msg-queue)]
      (loop [retry-msg nil
             retry-count 0]
        ; Terminate when msg-queue closed (no more messages)
        (when-let [msg (or retry-msg (<!! msg-throttle))]
          (let [retry-msg
                (let [response (do-request this (assoc msg :client client))]
                  (handle-response msg response retry-count retry-limit))]
            (if (some? retry-msg) 
              (recur retry-msg (inc retry-count))
              (recur nil 0))))))))

(defn- start-thread-pool
  [this]
  (sys-core/thread-pool
    (get-in this [:config :connections])
    (partial worker-thread this)))

(defn- handle-error
  [{status :status body :body :as response}]
  (let [message (if (empty? body)
                  (if (< 500 status)
                    "HTTP request error"
                    "HTTP server error")
                  body)]
    (ex-info message response)))

(defn- handle-no-data
  [response]
  {})

(defn- handle-unknown
  [response]
  (ex-info "Unknown HTTP response" response))

(defn xform-response
  "Returns a transducer to return parsed response data."
  [handlers]
  (fn [rf]
    (fn 
      ([] (rf))
      ([result] (rf result))
      ([result {:keys [error headers body status] :as response}]
       (if-let [out (cond 
                      (instance? Throwable response) response
                      (some? error) error
                      (= 204 status) ((or (:204 handlers) handle-no-data) response)
                      (= 404 status) ((or (:404 handlers) handle-no-data) response)
                      (= 200 status) ((or (:200 handlers) handle-unknown) response)
                      (= 300 status) ((or (:300 handlers) handle-unknown) response)
                      (= 500 status) ((or (:500 handlers) handle-error) response)
                      (<= 400 status) ((or (get handlers (keyword (str status))) handle-error) response)
                      :else 
                      (let [handler (or (get handlers (keyword (str status)))
                                        handle-unknown)]
                        (handler response)))]
         (rf result out)
         result)))))

(def identity-xform-response
  (xform-response
    {:200 identity}))

(defn request!
  ([this req]
   (request! this req (async/chan 1 identity-xform-response identity)))
  ([{:keys [msg-queue config]} req chan]
   (let [{:keys [proxy-url]} config
         req (cond-> (assoc req :result chan)
               (some? proxy-url) (assoc :proxy-url proxy-url))]
     (async/put! msg-queue req))
   chan))

(defrecord MultiHttpClient [config msg-queue thread-pool]
  PLifecycle

  (start [this]
    (log/debug {:message "Starting HTTP client"
                :name (resource/full-name this)})
    (let [{:keys [msg-queue-size]} (:config this)]
      (as-> this this
        (assoc this :msg-queue (async/chan (async/buffer msg-queue-size)))
        (assoc this :thread-pool (start-thread-pool this)))))

  (stop [this]
    (log/debug {:message "Stopping HTTP client"
                :name (resource/full-name this)})
    (async/close! msg-queue)
    (sys-core/wait-thread-pool thread-pool) 
    (dissoc this :msg-queue :thread-pool))

  )

(S/defn create
  [resource-name
   config :- Config]
  (resource/make-resource
    (map->MultiHttpClient {:config (merge default-config config)})
    resource-name))
