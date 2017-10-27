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

(defn nav-to-payment [driver amount]
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
        credit-row (get-element-text-el driver (get rows 1))
        checking-row (get-element-text-el driver (get rows 2))]
    {:credit (parse-money-line credit-row)
     :checking (parse-money-line checking-row)}))

(defn start-driver [& {:keys [debug?] :or {debug? false}}]
  (if debug?
    (boot-driver :chrome {:path "chromedriver.exe"})
    (headless)))

(defn make-crawler [user password]
  (let [start-req (chan)
        finish-res (chan)
        mfa-code-res (chan)
        mfa-code-req (chan)
        status-out (chan)
        amount-out (chan)
        transaction-req (chan)
        transaction-res (chan)
        init-state {:started false}
        state (atom init-state) ]
    (async/go-loop []
      (<! start-req)
      (log/info "STARTING CRAWLER")
      (swap! state assoc :started true)
      (swap! state assoc :driver (start-driver))
      (login (:driver @state) user password)
      (if (mfa-page? (:driver @state))
        (do
          (log/info "NEED MFA CODE")
          (>! mfa-code-req :req)
          (enter-mfa-code (:driver @state) (<! mfa-code-res))))
      (<! finish-res)
      (log/info "STOPPING CRAWLER")
      (quit (:driver @state))
      (reset! state init-state)
      (recur))
    {:start-req start-req
     :finish-res finish-res
     :mfa-code-res mfa-code-res
     :mfa-code-req mfa-code-req
     :state state}))
