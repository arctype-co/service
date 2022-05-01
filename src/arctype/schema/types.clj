(ns arctype.schema.types
  (:require
    [schema.core :as S]))

(defn Set
  [value-type]
  (S/pred set? "Set"))
