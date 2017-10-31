(ns credit-bot-clj.crawler.state)

(defn logged-in? [state]
  (= :success (:login state)))

(defn update-login [state login-update]
  (case login-update
    :mfa (assoc state :login login-update)
    :success (assoc state :login login-update)
    (assoc state :error (str "Improper login update: " login-update))))

(defn handle-error [f]
  "Looks for an error key and prints that rather than executing the logic"
  (fn [state & args]
    (let [new-state (apply f state args)
          error (:error state)]
      (if error
        (throw (Exeception. (str "Encountered error: " error)))
        state))))

(defn ensure-logged-in [f]
  "Higher order function that writes error to state if the user isn't logged in"
  (fn [state & args]
    (if (logged-in? state)
      (apply f state args)
      (assoc state :error "Must be logged in"))))

(defn mfa? [state]
  (= :mfa (:login state)))

(defn update-location [state loc]
  (assoc state :location loc))

(defn ensure-in-location [state loc]
  (if (= loc (:location state))
    (apply f state args)
    (assoc state :error (str "Wrong location, must be in " loc))))

(defn update-balances [state balances]
  (if (= :credit (:location state))
    (assoc state :balances balances)
    (assoc state :error "Must be on credit page")))

; Move to util functions, doesn't really deal with state
(defn safe-balance? [balances debit-minimum]
  "Checks balances and ensures it's okay to go ahead. If it's too low returns false"
  (let [{:keys [debit credit] balances}
        remaining (- debit credit)]
    (> remaining debit-minimum)))

(defn update-payment [state status]
  (case status
    :ok (assoc state :payment :ok)
    (assoc state :error "Improper payment update")))

; We can just plug the action as the second parameter, and make the actions
; communicate their results, just be explicit about it
(defn start-login [state login-update]
  (case login-update
    :success (-> state
                 (update-location :credit)
                 (assoc :login :ok))
    :mfa (assoc state :login :mfa)
    (assoc state :error "Didn't receive proper login update")))

(defn start-mfa [state mfa-update]
  (case mfa-update
    :success (-> state
                 (update-location :credit)
                 (assoc :login :ok))
    :fail (assoc state :mfa :retry)
    (assoc state :error "Didn't receive proper MFA update")))

