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
