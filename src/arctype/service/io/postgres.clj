(ns arctype.service.io.postgres
  (:require
    [cheshire.core :as json]
    [clojure.core.async :as async]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as log]
    [schema.core :as S]
    [sundbry.resource :as resource :refer [with-resources]]
    [arctype.service.util :refer [rmerge]]
    [arctype.service.protocol :refer :all])
  (:import
    [java.sql SQLException]
    [com.mchange.v2.c3p0 C3P0Registry ComboPooledDataSource DataSources AbstractConnectionCustomizer]
    [org.postgresql.util PGobject]
    [arctype.java.service.io.postgres ReadCommittedConnectionCustomizer]))

(def Config
  {:creds 
   {(S/optional-key :classname) (S/enum "org.postgresql.Driver")
    (S/optional-key :subprotocol) (S/enum "postgresql")
    :subname S/Str
    :user S/Str
    :password S/Str}
   (S/optional-key :max-idle-time-s) S/Int
   (S/optional-key :excess-idle-time-s) S/Int
   (S/optional-key :serializable?) S/Bool ; Enable serializable connection pool
   })

(def default-config
  {:max-idle-time-s (* 3 60 60) ; 3 hrs
   :excess-idle-time-s (* 30 60) ; 30 mins
   :serializable? false
   :creds {:classname "org.postgresql.Driver"
           :subprotocol "postgresql"}})

(def ^:private read-committed-customizer "arctype.java.service.io.postgres.ReadCommittedConnectionCustomizer")
(def ^:private serializable-customizer "arctype.java.service.io.postgres.SerializableConnectionCustomizer")

(defn- connect
  "Open a jdbc connection pool"
  [{creds :creds
    max-idle-time-s :max-idle-time-s
    excess-idle-time-s :excess-idle-time-s}
   customizer-class]
  (let [cpds (doto (ComboPooledDataSource.)
               (.setDriverClass (:classname creds)) 
               (.setJdbcUrl (str "jdbc:" (:subprotocol creds) ":" (:subname creds)))
               (.setUser (:user creds))
               (.setPassword (:password creds))
               ;; expire excess connections after 30 minutes of inactivity:
               (.setMaxIdleTimeExcessConnections excess-idle-time-s)
               ;; expire connections after 3 hours of inactivity:
               (.setMaxIdleTime max-idle-time-s)
               ;; Provide a connection customizer for explicit behavior
               (.setConnectionCustomizerClassName customizer-class))]
    {:datasource cpds}))

(defn- disconnect
  "Release a jdbc connection pool"
  [db]
  (DataSources/destroy (:datasource db)) 
  nil)

(defn health-check!
  [this]
  (jdbc/query (conn this) ["SELECT version()"]))

(defmacro try-sql
  [& body]
  `(try 
     (do ~@body)
     (catch SQLException e#
       (let [inner# (.getNextException e#)]
         (log/error e# {:message "SQL exception"
                        :cause (when (some? inner#)
                                 (.getMessage inner#))}))
       (throw e#))))

(defn json-obj
  [^PGobject obj]
  (when (some? obj)
    (json/decode (.getValue obj) true)))

(defn first-val
  "Get the first column of the first row"
  [results]
  (second (ffirst results)))

(defn serial-conn
  [this]
  (:serial-db this))

(defrecord PostgresClient [config db serial-db]
  PLifecycle
  (start [this]
    (log/info "Starting Postgres client")
    (cond-> this
        true (assoc :db (connect config read-committed-customizer))
        (:serializable? config) (assoc :serial-db (connect config serializable-customizer))))

  (stop [this]
    (log/info "Stopping Postgres client")
    (cond-> this
      (:serializable? config) (update :serial-db disconnect)
      true (update :db disconnect)))
  
  PJdbcConnection

  (conn [this]
    (:db this))

  )

(S/defn create
  [resource-name
   config :- Config]
  (resource/make-resource
    (map->PostgresClient
      {:config (rmerge default-config config)})
    resource-name))
