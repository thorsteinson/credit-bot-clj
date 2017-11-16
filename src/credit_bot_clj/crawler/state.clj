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
      (assoc state :error (str "Missing required key: " pred)))))

(defn handle-error [f]
  "Looks for an error key and prints that rather than executing the logic"
  (fn [state & args]
    (let [new-state (apply f state args)
          error (:error new-state)]
      (if error
        (throw (Exception. (str "Encountered error: " error)))
        state)))) 

(defn- success-wrapper [handler]
  "Helps with unwrapping updates from actions so they can be passed off to pure functions and the errors are written out to state if found"
  (fn [state handler-update]
    (let [result (:success handler-update)]
      (if result
        (handler state result)
        ; Error case
        (merge state handler-update)))))

(defn- skip-error [handler]
  "Short circuits evaluation if an error is in the state, so that it isn't overridden by even more errors"
  (fn [state handler-update]
    (if (:error state)
      state
      (handler state handler-update))))

(defn- make-handler
  ([handler pred]
    ((comp skip-error
           success-wrapper
           (partial must-have pred))
      handler))
  ([handler]
    ((comp skip-error success-wrapper) handler)))

;;;;;;; Handlers
(defn- handle-login-update [state result]
  (case result
    :mfa (assoc state :login :mfa)
    :acount (assoc state :login :complete)
    :login (assoc state :login :retry)
    (assoc state :error (str "Uknown update recieved: " result))))

(defn- handle-code-request [state code]
  (assoc state :code code))

(defn- handle-mfa-update [state result]
  (case result
    :retry-code (assoc state :login :mfa)
    :account (assoc state :login :complete)
    (assoc state :error (str "Unkown update recieved: " result))))

(defn- handle-amount-update [state balances]
  (if (safe-balance? balances (:debit-minimum state))
    (assoc state :balances balances)
    (assoc state :error (str "Balances aren't high enough: " balances))))

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
(def exec-login (make-handler handle-login-update))
(def exec-request-code (make-handler handle-code-request mfa?))
(def exec-mfa (make-handler handle-mfa-update))
(def exec-get-amounts (make-handler handle-amount-update logged-in?))
(def exec-confirmation (make-handler handle-confirmation-update :balances))
(def exec-payment (make-handler handle-payment-update :transaction-approval))
