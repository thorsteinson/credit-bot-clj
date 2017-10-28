(ns credit-bot-clj.crawler.state)

(defn logged-in? [state]
  (= :success (:login state)))

(defn update-login [state login-update]
  (case login-update
    :mfa (assoc state :login login-update)
    :success (assoc state :login login-update)
    (assoc state :error (str "Improper login update: " login-update))))

(defn ensure-logged-in [f state]
  (if (logged-in? state)
    (f state)
    (assoc state :error "Must be logged in")))

(defn mfa? [state]
  (= :mfa (:login state)))

(defn nav-to-credit [state]
  (assoc state :location :credit))

(defn set-balances [state balances]
  (if (= :credit (:location state))
    (assoc state :balances balances)
    (assoc state :error "Must be on credit page")))

(defn check-balances [balances debit-minimum debit-ratio]
  (let [{:keys [debit credit] balances}
        remaining (- debit credit)]
    (cond
      (< remaining debit-minimum) :error-debit-less-than-min
      (- debit credit))))

(defn pay-card [state]
  (if ()))

(-> state
    (update-login :mfa)
    (update-login :success)
    (nav-to-credit)
    (set-balances {:credit 50 :debit 80})
    ())
