(ns sundbry.service.io.sqlite
  (:require
    [cheshire.core :as json]
    [clojure.core.async :as async]
    [clojure.java.jdbc :as jdbc]
    [clojure.tools.logging :as log]
    [schema.core :as S]
    [sundbry.resource :as resource :refer [with-resources]]
    [sundbry.service.protocol :refer :all])
  (:import
    [java.sql SQLException]))

(def Config
  {:dbname S/Str})

(defn- connect
  "Open a jdbc connection pool"
  [config]
  ; http://clojure.github.io/java.jdbc/#clojure.java.jdbc/get-connection
  (let [params (merge {:dbtype "sqlite"} config)]
    (log/debug {:message "Opening JDBC connection"
                :params params})
    (jdbc/get-connection params)))

(defn- disconnect
  "Release a jdbc connection pool"
  [db]
  (log/debug {:message "Closing JDBC connection"})
  (.close db)
  nil)

(defn conn
  "Return a jdbc connection handle"
  [this]
  (:db this))

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

(defn first-val
  "Get the first column of the first row"
  [results]
  (second (ffirst results)))

(defrecord SQLiteClient [config db]
  PLifecycle
  (start [this]
    (log/info "Starting SQLite client")
    (-> this
        (assoc :db (connect config))))

  (stop [this]
    (log/info "Stopping SQLite client")
    (-> this
        (update :db disconnect))))

(S/defn create
  [resource-name config :- Config]
  (resource/make-resource
    (map->SQLiteClient
      {:config config})
    resource-name))
