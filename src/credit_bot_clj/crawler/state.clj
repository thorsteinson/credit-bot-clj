(ns credit-bot-clj.crawler.state)

(def init-state
  {:login :login
   :debit-minimum 500})

; Move to util functions, doesn't really deal with state
(defn safe-balance? [balances debit-minimum]
  "Checks balances and ensures it's okay to go ahead. If it's too low returns false"
  (let [{:keys [debit credit]} balances
        remaining (- debit credit)]
    (> remaining debit-minimum)))

;;;;;;; Higher Order Functions
(defn- must-have [pred handler]
  "Creates a handler that must have the specified predicate, or an error is returned"
  (fn [state handler-update]
    (if (pred state)
      (handler state handler-update)
      (throw Exception. (str "Missing required key: " pred)))))

;;;;;;; Handlers
(defn- handle-login-update [state result]
  (case result
    :mfa (assoc state :login :mfa)
    :acount (assoc state :login :complete)
    :login (assoc state :login :retry)
    (throw Exception. (str "Uknown update recieved: " result))))

(defn- handle-code-request [state code]
  (assoc state :code code))

(defn- handle-mfa-update [state result]
  (case result
    :retry-code (assoc state :login :mfa)
    :account (assoc state :login :complete)
    (throw Exception. (str "Unkown update recieved: " result))))

(defn- handle-amount-update [state balances]
  (if (safe-balance? balances (:debit-minimum state))
    (assoc state :balances balances)
    (throw Exception. (str "Balances aren't high enough: " balances))))

(defn- handle-confirmation-update [state confirmation]
  (assoc state :transaction-approval confirmation))

(defn- handle-payment-update [state payment]
  (assoc state :payment payment))

;;;;; Predicate Functions
(defn- logged-in? [state]
  (= :complete (:login state)))

(defn- mfa? [state]
  (= :mfa (:login state)))

;;;;; Exposed functions with all the bells and whistles composed
(def exec-login handle-login-update)
(def exec-request-code (must-have handle-code-request mfa?))
(def exec-mfa handle-mfa-update)
(def exec-get-amounts (must-have handle-amount-update logged-in?))
(def exec-confirmation (must-have handle-confirmation-update :balances))
(def exec-payment (must-have handle-payment-update :transaction-approval))
