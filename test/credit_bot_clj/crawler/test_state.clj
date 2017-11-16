(ns credit-bot-clj.crawler.test_state
  (:require [clojure.test :refer :all]
            [credit-bot-clj.crawler.state :refer :all]))

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
               :payment true})))
