(ns credit-bot-clj.crawler.actions
  (:require [etaoin.api :refer :all]
            [credit-bot-clj.utils :refer [parse-money-line]]))

(defn- mfa-page? [driver]
  (let [MFA-URL "https://onlinebanking.becu.org/BECUBankingWeb/mfa/challenge.aspx"]
    (wait 3)
    (= MFA-URL (get-url driver))))

(defn login! [driver credentials]
  (let [LOGIN_URL "https://onlinebanking.becu.org/BECUBankingWeb/login.aspx"]  
    (go driver LOGIN_URL))
  (let [USERNAME-INPUT {:id "ctlSignon_txtUserID"}
        PASSWORD-INPUT {:id "ctlSignon_txtPassword"}
        LOGIN-BTN {:id "ctlSignon_btnLogin"}
        {:keys [username password]} credentials]
    (fill driver USERNAME-INPUT username)
    (fill driver PASSWORD-INPUT password)
    (click driver LOGIN-BTN)
    ; There is also a bad login state that we should check for here
    (if (mfa-page? driver)
      ; Instead of returning mfa, we should call out failure explicity, and put
      ; MFA as a response buried within the success payload. How else do we handle
      ; the situation when we enter bad credentials?
      :mfa
      :success)))

(defn login-with-code! [driver code]
  (let [CODE-INPUT {:id "challengeAnswer"}
        CONTINUE-BTN {:id "mfa_btnAnswerChallenge"}]
    (fill driver CODE-INPUT code)
    (click driver CONTINUE-BTN)
    (if (mfa-page? driver)
      :fail
      ; Success should really be defined as on the payment page. Which we don't check for
      ; We should have a function that waits for n secs and then returns the URL
      ; for maximum flexibility
      :success)))

(defn- nav-to-credit [driver]
  (let [VISA-TABLE {:id "visaTable"}
        PAY-BTN {:id "btnPayNow"}
        CONTINUE-BTN {:id "ctlWorkflow_btnAddNext"}
        ACCOUNT-SELECT {:id "ctlWorkflow_ddlAddFromAccount"}]
    (click driver [VISA-TABLE
                   {:class "item"}
                   {:tag "a"}])
    (click driver PAY-BTN)
    (click driver [ACCOUNT-SELECT {:tag "option" :index 2}])
    (click driver CONTINUE-BTN)
    driver))

(defn pay! [driver amount]
  (let [OTHER-AMOUNT {:id "ctlWorkflow_rdoPrincipalOnly1"}
        OTHER-INPUT {:id "ctlWorkflow_txtAddTransferAmountCredit"}
        FREQ-SELECT {:id "ctlWorkflow_ddlAddFrequency1"}
        CONTINUE-BTN {:id "ctlWorkflow_btnAddVerifyAddTransfer"}
        CONFIRM_BTN {:id "ctlWorkflow_btnAddConfirmAddPayment"}]
    (click driver OTHER-AMOUNT)
    (fill driver OTHER-INPUT amount)
    (click driver [FREQ-SELECT {:tag "option" :index 2}])
    (click driver CONTINUE-BTN)
    (click driver CONFIRM_BTN)
    ; TODO: Add some type of check
    :success))

(defn- extract-amounts [driver]
  (let [rows (query-all driver {:tag "tr"})
        credit-row (get-element-text-el driver (get rows 2))
        checking-row (get-element-text-el driver (get rows 3))]
    ; TODO: Add some type of check
    ; Even though we return a response, it should be encapsulated in
    ; something that can represent failure
    {:credit (parse-money-line credit-row)
     :checking (parse-money-line checking-row)}))

; As long as the driver is output, we can compose these actions nicely
(def get-amounts! (comp extract-amounts nav-to-credit))
