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
        (make-crawler {:username "calebt5"
                       :password "eaIkmhMu3TJO2T8uX8o3"}
                      {:debug? true
                       :amount-override 0.01})]
    (println (<!! finish-req))))
