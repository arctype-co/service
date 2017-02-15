(ns sundbry.service.io.http.compojure-api
  (:require
    [clojure.string :as string]
    [clojure.tools.logging :as log]
    [cheshire.core :as json]
    [compojure.core :as compojure :refer [ANY GET POST]]
    [compojure.route :as route]
    [compojure.handler :as handler]
    [ring.util.response :as response]
    [ring.middleware.cors :as cors]
    [ring.middleware.json :refer [wrap-json-params]]
    [ring.middleware.params :refer [wrap-params]]
    [ring.middleware.keyword-params :refer [wrap-keyword-params]]
    [ring.middleware.multipart-params :refer [wrap-multipart-params]]
    [schema.core :as S]
    [sundbry.resource :as resource :refer [with-resources]]
    [sundbry.service.protocol :refer :all]))

(def Config
  (S/maybe {}))

(def ^:private default-config
  {})

(def ^:private no-cache-headers
  {"Cache-Control" "public, max-age=0, no-cache, no-store, must-revalidate"})

(defprotocol PHttpService
  (routes [this]))

(defn- wrap-cors [handler]
  (let [handler (cors/wrap-cors handler
                  :access-control-allow-origin [#".*"]
                  :access-control-allow-methods [:head :get :post])]
    (fn [req]
      (-> (handler req)
          (response/header "Access-Control-Allow-Credentials" "false")))))

(defn- internal-error-message
  "Return an error message safe to show to the end user"
  [ex]
  "Chowdr service error")

(defn- input-schema-error?
  "Is this exeption a schema error on input?"
  [ex]
  (and (= ::schema.core/error (:type (ex-data ex)))
       (.startsWith (.getMessage ex) "Input")))

(defn- wrap-errors
  [this handler]
  (with-resources this ["alert"]
    (fn [req]
      (try 
        (handler req)
        (catch Throwable e
          (ring-exception alert e req)
          (let [ex-dat (ex-data e)]
            (cond
              (input-schema-error? e)
              (do 
                (log/warn e "Request schema error")
                {:status 400
                 :body {:message (str "Invalid client request: " (.getMessage e))}})

              (and (some? ex-dat) (some? (:http-status ex-dat))
                   (< (:http-status ex-dat) 500))
              (do
                (log/warn e "Request exception")
                {:status (:http-status ex-dat)
                 :body {:message (.getMessage e)}})

              :else
              (do
                (log/error e "Internal server error")
                {:status (or (when (some? ex-dat) (:http-status ex-dat)) 500)
                 :body {:message (internal-error-message e)}}))))))))

(defn- wrap-tracing [handler]
  (fn [req]
    (let [res (handler req)]
      (log/trace {:request req
                  :response (print-str res)})
      res)))

(defn- json-response
  [response]
  "Convert responses with map bodies to json"
  (if (map? (:body response))
    (-> response
        (update :body json/encode)
        (update :headers assoc "Content-Type" "application/json"))
    response))

(defn- wrap-json-response [handler]
  (fn [req]
    (json-response (handler req))))

(defn- make-handler [{:keys [routers] :as this}]
  (let [wrap-errors-with-this (partial wrap-errors this)]
    (-> #_(apply compojure/routes
               (concat
                 (mapcat #(proto/routes (resource/require this %)) routers)))
        wrap-errors-with-this
        wrap-json-response
        wrap-tracing
        wrap-keyword-params 
        wrap-json-params
        wrap-multipart-params
        wrap-params
        wrap-cors 
        handler/api)))

(defn client-ip
  "Get the original client IP from a request object."
  [{remote-addr :remote-addr
    {x-forwarded-for "x-forwarded-for"} :headers}]
  (if (empty? x-forwarded-for)
    remote-addr
    (string/trim (first (string/split x-forwarded-for #",")))))

(defrecord CompojureApi [config routers]

  PHttpHandler
  (ring-handler [this]
    nil
    )

  ) ; end record


(S/defn create
  [resource-name
   config :- Config]
  (let [config (merge default-config config)]
    (resource/make-resource
      (map->CompojureApi {:config config})
      resource-name
      #{:alert})))
