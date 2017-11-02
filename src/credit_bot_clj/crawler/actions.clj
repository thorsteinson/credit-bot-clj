(ns credit-bot-clj.crawler.actions
  (:require [etaoin.api :refer :all]
            [credit-bot-clj.utils :refer [parse-money-line]]))

(defn- handle-exception [f]
  (fn [& args]
    (try
      (let [result (apply f args)]
        (if (:error result)
          result
          {:success result})
      (catch Exception e {:error e})))))

(defn- mfa-page? [driver]
  (let [MFA-URL "https://onlinebanking.becu.org/BECUBankingWeb/mfa/challenge.aspx"]
    (wait 3)
    (= MFA-URL (get-url driver))))

(defn- login [driver credentials]
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
      :mfa
      :logged-in)))

(defn- login-with-code [driver code]
  (let [CODE-INPUT {:id "challengeAnswer"}
        CONTINUE-BTN {:id "mfa_btnAnswerChallenge"}]
    (fill driver CODE-INPUT code)
    (click driver CONTINUE-BTN)
    (if (mfa-page? driver)
      :incorrect-code
      :logged-in)))

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

(defn- pay [driver amount]
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
    ))

(defn- extract-amounts [driver]
  (let [rows (query-all driver {:tag "tr"})
        credit-row (get-element-text-el driver (get rows 2))
        checking-row (get-element-text-el driver (get rows 3))]
    ; TODO: Add some type of check
    {:credit (parse-money-line credit-row)
     :checking (parse-money-line checking-row)}))

(def get-amounts!     (comp handle-exception extract-amounts nav-to-credit))
(def pay!             (comp handle-exception pay))
(def login-with-code! (comp handle-exception login-with-code))
(def login!           (comp handle-exception login))
