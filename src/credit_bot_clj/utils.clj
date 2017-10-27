(ns credit-bot-clj.utils)

(defn parse-money-line [text]
  (let [formatter (java.text.NumberFormat/getInstance java.util.Locale/ENGLISH)
        money-pattern #"\$(\d+(\d|,)*\.\d\d)"]
    (->> text
         (re-find money-pattern)
         (second)
         (.parse formatter))))
