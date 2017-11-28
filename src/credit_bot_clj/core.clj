(ns credit-bot-clj.core
  (:require [credit-bot-clj.crawler.core :refer :all]
            [clojure.core.async :refer [>!! <!! >! <! go]])
  (:gen-class))

(defn -main
  [& args]
  (let [{:keys [finish-req
                mfa-code-res
                mfa-code-req
                transaction-req
                transaction-res]}
        (make-crawler {:username (System/getenv "BECU_USER")
                       :password (System/getenv "BECU_PASS")}
                      {:debug? true
                       :amount-override 0.01
                       :debit-minimum 500})]
    (println (<!! finish-req))))
