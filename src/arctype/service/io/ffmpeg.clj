(ns ^{:doc
      "FFMpeg Driver
      Derived from https://github.com/runexec/ffmpeg-clj"}
  arctype.service.io.ffmpeg
  (:require 
    [clojure.core.async :as async]
    [clojure.java.shell :refer [sh]]
    [clojure.string :as s]))

(def ^:dynamic *bin* "ffmpeg")

(defn cmd [& argv] 
  (->> argv
       (map #(if (keyword? %) (str "-" (name %)) (str %)))
       (into [*bin*])))

(defn bin [] *bin*)

(defn ffmpeg! [& args]
  (let [cmd! (apply sh (apply cmd args))
        {:keys [exit out err]} cmd!]
    (when-not (zero? exit)
      (throw
       (Exception. err)))
      out))

(defn version [] 
  (as-> (ffmpeg! "-version") o
    (re-find #"version \S+" o)
    (s/split o #" ")
    (last o)))

(defn ffmpeg-thread! [& args]
  (async/thread
    (try (apply ffmpeg! args)
         (catch Exception e e))))
