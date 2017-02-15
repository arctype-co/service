(ns sundbry.service.io.http.http-kit
  (:require
    [clojure.tools.logging :as log]
    [org.httpkit.server :as http-kit]
    [schema.core :as S]
    [sundbry.resource :as resource]
    [sundbry.service.protocol :refer :all]))

(def Config
  {(S/optional-key :ip) S/Str ; which IP to bind, default to 0.0.0.0
   (S/optional-key :port) S/Int ; which port listens for incoming requests, default to 8090
   (S/optional-key :thread) S/Int ; How many threads to compute response from request, default to 4
   })

(defrecord HttpKitServer [config handler-name httpd]
  PLifecycle
  (start [this]
    (log/info (assoc config
                     :message "Starting HTTP server"))
    (let [handler (ring-handler (resource/require this handler-name))]
      (assoc this :httpd (http-kit/run-server handler config))))

  (stop [this]
    (log/info {:message "Stoppping HTTP server"})
    (httpd)
    (dissoc this :httpd))
  
  )

(S/defn create
  "Handler-name: name of resource providing ring http handler"
  [resource-name 
   config :- Config
   handler-name]
  (resource/make-resource 
    (map->HttpKitServer {:config config :handler-name handler-name})
    resource-name
    #{handler-name}))
