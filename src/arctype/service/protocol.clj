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

; Provide default implementation
(extend-type Object
  PLifecycle
  (start [this] this)
  (stop [this] this))
