(ns ^{:doc
      "FFMpeg Driver
      Derived from https://github.com/runexec/ffmpeg-clj"}
  arctype.service.io.ffmpeg
  (:require 
    [clojure.core.async :as async]
    [clojure.java.io :refer [reader]]
    [clojure.string :as s]))

(def ^:dynamic *bin* "ffmpeg")

(defn- cmd [argv] 
  (->> argv
       (map #(if (keyword? %) (str "-" (name %)) (str %)))
       (into [*bin*])))

(defn start-ffmpeg! [& args]
  (-> (Runtime/getRuntime)
      (.exec (into-array String (cmd args)))))

(defn wait-ffmpeg! [proc]
  (let [stdin (.getOutputStream proc)
        stderr (.getErrorStream proc)
        stdout (.getInputStream proc)
        _ (.close stdin)
        exit (.waitFor proc)]
    (if (zero? exit)
      (slurp (reader stdout))
      (throw
        (let [error-buf (slurp (reader stderr))]
          (ex-info "FFmpeg error" {:status exit
                                   :error error-buf}))))))

(defn version [] 
  (as-> (-> (start-ffmpeg! "-version")
            (wait-ffmpeg!)) out
    (re-find #"version \S+" out)
    (s/split out #" ")
    (last out)))

(defn ffmpeg-thread! [timeout-ms & args]
  (async/go
    (let [proc (apply start-ffmpeg! args)
          wait-chan (async/thread
                      (try (wait-ffmpeg! proc)
                           (catch Exception e e)))
          timeout-chan (async/timeout timeout-ms)
          [result result-chan] (async/alts!! [wait-chan timeout-chan] :priority true)]
      (if (= result-chan timeout-chan)
        (do
          (.destroy proc)
          (ex-info "FFmpeg timeout!" {:timeout-ms timeout-ms}))
        result))))
