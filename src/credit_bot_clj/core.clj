(ns credit-bot-clj.core
  (:require [credit-bot-clj.crawler.core :refer :all]
            [clojure.core.async :refer [>!! <!! >! <! go]]
            [clojure.string :as string])
  (:gen-class))

(defn -main
  [& args]
  (let [{:keys [state
                finish-req
                start-req
                mfa-code-res
                mfa-code-req
                transaction-req
                transaction-res]}
        (make-crawler {:username (System/getenv "BECU_USER")
                       :password (System/getenv "BECU_PASS")}
                      {:debug? (System/getenv "BECU_DEBUG")
                       :amount-override 0.01
                       :debit-minimum 500})]
    ; Start the crawler
    (>!! start-req :request)

    (loop []
      ; Check every second
      (Thread/sleep 1000)
      (let [login-status (get @state :login)]
        (case login-status
          :complete nil
          :mfa  (do
                  (println "Please input code")
                  (>!! mfa-code-res (read-string (read-line))))
          (recur))))
    (loop [msg "Approve transaction? (Y/N)"]
      (println msg)
      (let [response (string/lower-case (read-line))
            approve #(>!! transaction-res true)
            disapprove #(>!! transaction-res false)]
        (condp = response
          "yes" (approve)
          "ye" (approve)
          "y" (approve)
          "no" (disapprove)
          "n" (disapprove)
          (recur "Didn't get a yes or no, please enter again"))))
    (println (<!! finish-req))))
