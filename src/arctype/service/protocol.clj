(ns arctype.service.protocol)

(defprotocol PLifecycle
  (start [_])
  (stop [_]))

(defprotocol PAlertHandler
  (exception [this ex])
  (ring-exception [this ex request]))

(defprotocol PHttpHandler
  (ring-handler [this]))

(defprotocol PJdbcConnection
  (conn [this]))

(defprotocol PEventProducer
  (put-event! [this topic data]))

(defprotocol PEventConsumer
  (start-event-consumer [this topic options handler])
  (stop-event-consumer [this consumer]))

(defprotocol PClientDecorator
  (client [this]))

; Provide default implementations
(extend-type Object
  PLifecycle
  (start [this] this)
  (stop [this] this))
