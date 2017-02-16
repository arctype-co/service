(ns sundbry.service.model.security
  (:require
    [schema.core :as S])
  (:import
    [java.nio ByteBuffer]
    [java.security MessageDigest SecureRandom]
    [org.apache.commons.codec.binary Base64]))

(def ^:private ^SecureRandom srng (SecureRandom.))

(defn secure-id
  "Generate a random, URL-safe base-64 encoded ID string."
  ([] (secure-id 8)) ; generates a 64-bit id
  ([n-bytes]
   (let [buf (byte-array n-bytes)]
     (.nextBytes srng buf)
     (Base64/encodeBase64URLSafeString buf))))

(defn secure-numeric-str
  "Generate a random numeric string"
  [n-digits]
  (loop [^StringBuilder sb (StringBuilder.)
         n n-digits]
    (if (pos? n)
      (recur (.append sb (mod (.nextInt srng) 10)) (dec n))
      (.toString sb))))

(defn long-id
  "Parse an 8-byte id string into a 64 bit (long) id."
  [^String string-id-val]
  ; Since we have some old UUID-form ids hanging around our database,
  ; We handle them gracefully by truncating into a base64 id.
  (let [string-id-val (if (< 11 (count string-id-val))
                        (-> string-id-val 
                            (.replaceAll "-" "")
                            (.substring 0 11))
                        string-id-val)
        buf (ByteBuffer/wrap (Base64/decodeBase64 string-id-val))]
    (.getLong buf)))

(defn string-id
  "Transform a long id to a string"
  [^long long-id-val] 
  (let [buf (ByteBuffer/allocate 8)]
    (.putLong buf long-id-val)
    (Base64/encodeBase64URLSafeString (.array buf))))
