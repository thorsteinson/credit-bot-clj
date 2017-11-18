(ns credit-bot-clj.utils
  (:require [clojure.core.async :as async :refer [go >! <! alts! timeout]]))

(defn parse-money-line [text]
  (let [formatter (java.text.NumberFormat/getInstance java.util.Locale/ENGLISH)
        money-pattern #"\$(\d+(\d|,)*\.\d\d)"]
    (->> text
         (re-find money-pattern)
         (second)
         (.parse formatter))))

(defn make-request 
  "Make a request to the given channel, and throw an exception if we exceed
  the specified timeout"
  ([t req-chan]
    (make-request t req-chan :request))
  ([t req-chan req-code]
    (go
      (let [result (alts! [[req-chan x]
                          (timeout t)])]
        (if result
          result
          (throw Exception. (str "Request timed out after " t " seconds")))))))

(defn make-response [t res-chan]
  (go
    (let [result (alts! [res-chan
                         (timeout t)])]
      (if result
        result
        (throw Exception. (str "Request timeout after " t " seconds"))))))

(defn get-response [t req-chan res-chan]
  (make-request t req-chan)
  (make-response t res-chan))

(def req (partial make-request 3000))
(def req-res (partial get-response 3000))
