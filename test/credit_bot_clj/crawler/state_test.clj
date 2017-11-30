(ns credit-bot-clj.crawler.state-test
  (:require [clojure.test :refer :all]
            [credit-bot-clj.crawler.state :as S :refer :all]
            [clojure.spec.alpha :as s]))

(s/check-asserts true)

(deftest full-path
  (let [final-state (merge init-state
                           {:login :complete
                           :code 777
                           :balances {:credit 1000 :debit 2000}
                           :transaction-approval true
                           :payment true})]
    (is (= (-> init-state
              (exec-login :mfa)
              (exec-request-code 72141)
              (exec-mfa :retry-code)
              (exec-request-code 777)
              (exec-mfa :account)
              (exec-get-amounts {:credit 1000 :debit 2000})
              (exec-confirmation true)
              (exec-payment true))
           final-state))))

(deftest respects-debit-minimum
  (let [state {:login :complete
               :debit-minimum 1000
               :code 1232 }
        low-balances {:credit 100 :debit 100}
        high-balances {:credit 100 :debit 1200}]
    (is (thrown? Exception (exec-get-amounts state low-balances))
        "The debit minimum should trigger an error")
    (is (= (exec-get-amounts state high-balances)
           (merge state {:balances high-balances}))
        "The high balance reaches the debit minimum and should succeed" )))

(deftest must-have-confirmation-to-pay
  (let [state {}]
    (is (thrown? Exception (exec-payment state :something)))))
