(ns ^{:doc "Core system patterns"}
  arctype.service.core
  (:require
    [clojure.core.async :as async]
    [arctype.service.util :refer [<??]]))

(defn thread-pool
  [num-threads make-thread]
  (doall (for [i (range num-threads)]
           (make-thread i))))

(defn wait-thread-pool
  [thread-pool]
  (doseq [thread thread-pool]
    (<?? thread)))

(defn wait-thread-pool-timeout
  [thread-pool timeout-ms]
  (doseq [thread thread-pool]
    (async/alts!! [thread (async/timeout timeout-ms)])))
