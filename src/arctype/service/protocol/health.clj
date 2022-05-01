(ns arctype.service.protocol.health
  (:require
    [schema.core :as S]))

(S/defprotocol PHealthy
  (healthy? :- S/Bool [_]))
