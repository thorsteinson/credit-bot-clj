(ns credit-bot-clj.crawler
  (:require [etaoin.api :refer :all]
            [etaoin.keys :as k]
            [clojure.core.async :as async :refer [chan >!! <!! >! <! take! put!]]
            [clojure.tools.logging :as log]))

(defn login [driver, user, password]
  (let [LOGIN_URL "https://onlinebanking.becu.org/BECUBankingWeb/login.aspx"]  
    (go driver LOGIN_URL))
  (let [USERNAME-INPUT {:id "ctlSignon_txtUserID"}
        PASSWORD-INPUT {:id "ctlSignon_txtPassword"}
        LOGIN-BTN {:id "ctlSignon_btnLogin"}]
    (fill driver USERNAME-INPUT user)
    (fill driver PASSWORD-INPUT password)
    (click driver LOGIN-BTN)))

(defn enter-mfa-code [driver code]
  (let [CODE-INPUT {:id "challengeAnswer"}
        CONTINUE-BTN {:id "mfa_btnAnswerChallenge"}]
    (fill driver CODE-INPUT code)
    (click driver CONTINUE-BTN)))

(defn mfa-page? [driver]
  (let [MFA-URL "https://onlinebanking.becu.org/BECUBankingWeb/mfa/challenge.aspx"]
    (wait 3)
    (= MFA-URL (get-url driver))))

(defn nav-to-credit [driver]
  (let [VISA-TABLE {:id "visaTable"}
        PAY-BTN {:id "btnPayNow"}
        CONTINUE-BTN {:id "ctlWorkflow_btnAddNext"}
        ACCOUNT-SELECT {:id "ctlWorkflow_ddlAddFromAccount"}]
    (click driver [VISA-TABLE
                   {:class "item"}
                   {:tag "a"}])
    (click driver PAY-BTN)
    (click driver [ACCOUNT-SELECT {:tag "option" :index 2}])
    (click driver CONTINUE-BTN)))

(defn exec-payment [driver amount]
  (let [OTHER-AMOUNT {:id "ctlWorkflow_rdoPrincipalOnly1"}
        OTHER-INPUT {:id "ctlWorkflow_txtAddTransferAmountCredit"}
        FREQ-SELECT {:id "ctlWorkflow_ddlAddFrequency1"}
        CONTINUE-BTN {:id "ctlWorkflow_btnAddVerifyAddTransfer"}
        CONFIRM_BTN {:id "ctlWorkflow_btnAddConfirmAddPayment"}]
    (click driver OTHER-AMOUNT)
    (fill driver OTHER-INPUT amount)
    (click driver [FREQ-SELECT {:tag "option" :index 2}])
    (click driver CONTINUE-BTN)
    (click driver CONFIRM_BTN)))

(defn parse-money-line [text]
  (let [formatter (java.text.NumberFormat/getInstance java.util.Locale/ENGLISH)
        money-pattern #"\$(\d+(\d|,)*\.\d\d)"]
    (->> text
         (re-find money-pattern)
         (second)
         (.parse formatter))))

(defn extract-amounts [driver]
  (let [rows (query-all driver {:tag "tr"})
        credit-row (get-element-text-el driver (get rows 2))
        checking-row (get-element-text-el driver (get rows 3))]
    {:credit (parse-money-line credit-row)
     :checking (parse-money-line checking-row)}))

(defn start-driver [debug?]
  (if debug?
    (boot-driver :chrome {:path "chromedriver.exe"})
    (headless)))

(defn make-crawler
  ([user password]
    (make-crawler user password {:debug? false}))
  ([user password opts]
    (let [start-req (chan 1)
          finish-res (chan 1)
          mfa-code-res (chan 1)
          mfa-code-req (chan 1)
          status-out (chan 1)
          amount-out (chan 1)
          transaction-req (chan 1)
          transaction-res (chan 1)
          init-state {:started false}
          state (atom init-state) ]
      (async/go-loop []
        (<! start-req)
        (log/info "STARTING CRAWLER")
        (swap! state assoc :started true)
        (swap! state assoc :driver (start-driver (:debug? opts)))
        (login (:driver @state) user password)
        (while (mfa-page? (:driver @state))
          (do
            (>! mfa-code-req :req)
            (log/info "REQUESTED MFA")
            (enter-mfa-code (:driver @state) (<! mfa-code-res))))
        (nav-to-credit (:driver @state))
        (swap! state assoc :payment (extract-amounts (:driver @state)))
        (>! transaction-req :trans-req) 
        (log/info "Waiting for transaction approval response")
        (<! transaction-res) 
        (exec-payment (:driver @state)
                      (get-in @state [:payment :credit]))
        (log/info "STOPPING CRAWLER")
        (quit (:driver @state))
        (reset! state init-state)
        (recur))
      {:start-req start-req
       :finish-res finish-res
       :transaction-req transaction-req
       :transaction-res transaction-res
       :mfa-code-res mfa-code-res
       :mfa-code-req mfa-code-req
       :state state})))
