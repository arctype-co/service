(ns ^{:doc "ImageMagick im4java interface"}
  arctype.service.io.im
  (:import
    [org.im4java.core ConvertCmd IMOperation Info]))

(defn basic-info
  [path]
  (Info. path true))

; Options: 
; quality: http://www.imagemagick.org/script/command-line-options.php#quality
(defn resize! 
  [in-path out-path w h options]
  (let [cmd (ConvertCmd.)
        op (IMOperation.)]
    (.addImage op (into-array String [(str in-path)]))
    (.resize op (int w) (int h))
    (when-let [quality (:quality options)] 
      (.quality op (double quality)))
    (.addImage op (into-array String [(str out-path)]))
    (.run cmd op (into-array Object []))))
