(ns credit-bot-clj.crawler.actions
  (:require [etaoin.api :refer :all]
            [credit-bot-clj.utils :refer [parse-money-line]]))

(defn- get-page [{:keys [driver]}]
  (let [LOGIN-URL "https://onlinebanking.becu.org/BECUBankingWeb/Login.aspx"
        MFA-URL "https://onlinebanking.becu.org/BECUBankingWeb/mfa/challenge.aspx"
        ACCOUNT-URL "https://onlinebanking.becu.org/BECUBankingWeb/Accounts/Summary.aspx"]
    (wait 1)
    (case (get-url driver)
      LOGIN-URL :login
      MFA-URL :mfa
      ACCOUNT-URL :account
      :unknown)))

(defn- login [{:keys driver credentials}]
  (let [LOGIN_URL "https://onlinebanking.becu.org/BECUBankingWeb/login.aspx"]  
    (go driver LOGIN_URL))
  (let [USERNAME-INPUT {:id "ctlSignon_txtUserID"}
        PASSWORD-INPUT {:id "ctlSignon_txtPassword"}
        LOGIN-BTN {:id "ctlSignon_btnLogin"}
        {:keys [username password]} credentials]
    (doto driver
          (fill USERNAME-INPUT username)
          (fill PASSWORD-INPUT password)
          (click LOGIN-BTN))
    (case (get-page driver)
      :mfa :mfa
      :account :account
      :login :login
      (throw Exception. (str "Login error recieved unknown keyword: " (get-page driver))))))

(defn- login-with-code [{:keys driver code}]
  (let [CODE-INPUT {:id "challengeAnswer"}
        CONTINUE-BTN {:id "mfa_btnAnswerChallenge"}]
    (doto driver
          (fill CODE-INPUT code)
          (click  CONTINUE-BTN))
    (case (get-page driver)
      :account :account
      :mfa :mfa
      (throw Exception. "Redirected to unknown page" ))))

; TODO: Add a check here, how did I miss this?
(defn- nav-to-credit [{:keys driver}]
  (let [VISA-TABLE {:id "visaTable"}
        PAY-BTN {:id "btnPayNow"}
        CONTINUE-BTN {:id "ctlWorkflow_btnAddNext"}
        ACCOUNT-SELECT {:id "ctlWorkflow_ddlAddFromAccount"}]
    (doto driver
          (click [VISA-TABLE {:class "item"} {:tag "a"}])
          (click PAY-BTN)
          (click [ACCOUNT-SELECT {:tag "option" :index 2}])
          (click CONTINUE-BTN))))

(defn- pay [{:keys driver {:keys balances}}]
  (let [OTHER-AMOUNT {:id "ctlWorkflow_rdoPrincipalOnly1"}
        OTHER-INPUT {:id "ctlWorkflow_txtAddTransferAmountCredit"}
        FREQ-SELECT {:id "ctlWorkflow_ddlAddFrequency1"}
        CONTINUE-BTN {:id "ctlWorkflow_btnAddVerifyAddTransfer"}
        CONFIRM_BTN {:id "ctlWorkflow_btnAddConfirmAddPayment"}]
    (doto driver
          (click OTHER-AMOUNT)
          (fill OTHER-INPUT amount)
          (click [FREQ-SELECT {:tag "option" :index 2}])
          (click CONTINUE-BTN)
          (click CONFIRM_BTN)))
  (let [text (get-element-text driver
              {:class "instructions container"
              :tag "div"})
        valid-str "Your payment request has been submitted"
        pattern (re-pattern valid-str)]
    (wait 1)
    (if-not (re-find pattern text)
      (throw Exception. "Didn't recieve validation message from BECU" ))))

(defn- extract-amounts [{:keys driver}]
  (let [rows (query-all driver {:tag "tr"})
        credit-row (get-element-text-el driver (get rows 2))
        checking-row (get-element-text-el driver (get rows 3))]
    {:credit (parse-money-line credit-row)
     :checking (parse-money-line checking-row)}))

(defn start-driver! [{:keys debug?}]
  (if debug?
    (boot-driver :chrome {:path "chromedriver.exe"})
    (headless)))

(def get-amounts!     (comp extract-amounts nav-to-credit))
(def pay!             pay)
(def login-with-code! login-with-code)
(def login!           login)
