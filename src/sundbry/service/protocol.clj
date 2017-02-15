(ns sundbry.service.protocol)

(defprotocol Lifecycle
  (start [_])
  (stop [_]))
