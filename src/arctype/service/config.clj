(ns ^{:doc "Config settings"}
  arctype.service.config
  (:refer-clojure :exclude [read])
  (:require
    [clojure.java.io :as io]
    [clojure.tools.logging :as log]
    [clojure.walk :as walk]
    [yaml.core :as yaml]
    [schema.core :as S]))

(defn environment-config-path
  []
  (System/getenv "SERVICE_CONFIG"))

(def ^:dynamic *config-file*
  (or (environment-config-path) "resources/config.yml"))

(defn- recursive-read-conf
  [file-path]
  (let [cfg (yaml/from-file file-path)
        cfg (walk/keywordize-keys cfg)]
    (apply merge
           (dissoc cfg :include)
           (when (some? (:include cfg))
             (map recursive-read-conf (:include cfg))))))

(S/defn read
  "Returns a parsed config.yml path."
  ([] (read *config-file*))
  ([path]
   (try 
     (log/info (str "Loading service configuration from " path))
     (recursive-read-conf path) 
     (catch Exception ex
       (throw (ex-info (str "Error reading " path ": " (.getMessage ex))
                       {:inner ex
                        :file path}))))))
