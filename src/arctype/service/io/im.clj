(ns ^{:doc "ImageMagick im4java interface"}
  arctype.service.io.im
  (:import
    [org.im4java.core Info]))

(defn basic-info
  [path]
  (Info. path true))
