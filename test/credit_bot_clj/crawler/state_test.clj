(ns credit-bot-clj.crawler.state-test
  (:require [clojure.test :refer :all]
            [credit-bot-clj.crawler.state :refer :all]))

(deftest full-path
  (is (= (-> init-state
            (exec-login {:success :mfa})
            (exec-request-code {:success 72141})
            (exec-mfa {:success :retry-code})
            (exec-request-code {:success 777})
            (exec-mfa {:success :account})
            (exec-get-amounts {:success {:credit 1000 :debit 2000}})
            (exec-confirmation {:success true})
            (exec-payment {:success true}))
        (merge init-state
                {:login :complete
                :code 777
                :balances {:credit 1000 :debit 2000}
                :transaction-approval true
                :payment true}))))

(deftest handles-errors
  (is (= (-> init-state
            (exec-login {:error "This is a problem"})
            (exec-mfa {:error "Another error"}))
        (merge init-state {:error "This is a problem"}))))

(deftest respects-debit-minimum
  (let [state {:login :complete
               :debit-minimum 1000
               :code 1232 }
        low-balances {:credit 100 :debit 100}
        high-balances {:credit 100 :debit 1200}]
    (is (:error (exec-get-amounts state {:success low-balances}))
        "The debit minimum should trigger an error")
    (is (nil? (:error (exec-get-amounts state {:success high-balances})))
        "The high balance reaches the debit minimum and should succeed" )))

(deftest must-have-confirmation-to-pay
  (let [state {}]
    (is (:error (exec-payment state {:success :something})))))
