(ns credit-bot-clj.crawler.state
  (:require [clojure.spec.alpha :as s]))

;;;;;;;;;;;;; Spec Defintions

;; Credentials
(s/def ::non-empty-string (s/and string? #(not= "")))
(s/def ::username ::non-empty-string)
(s/def ::password ::non-empty-string)
(s/def ::credentials (s/keys :req-un [::username ::password]))

;; Balances
(s/def ::number+ (s/and number? (partial < 0)))
(s/def ::credit ::number+)
(s/def ::debit ::number+)
(s/def ::balances (s/keys :req-un [::credit ::debit]))

;; Logins
(s/def ::login #{:complete :retry :mfa})
(s/def ::login-update #{:mfa :account :login})
(s/def ::mfa-update #{:retry-code :account})

;; Request code
(s/def ::code ::number+)

;; Transaction approval
(s/def ::transaction-approval #{true false})

;; Etc
(s/def ::debit-minimum :number+)

(s/def ::state (s/keys :req-un [::credentials]
                       :opt-un [::balances ::login ::code
                                ::debit-minimum]))


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
      ; Technically they could have the key, the value could be nil or false, which is behaviour that we rely on
      (throw (Exception. (str "Missing required key: " pred))))))

;;;;;;; Handlers
(defn- handle-login-update [state result]
  (case result
    :mfa (assoc state :login :mfa)
    :account (assoc state :login :complete)
    :login (assoc state :login :retry)
    (throw (Exception. (str "Uknown update recieved: " result)))))

(defn- handle-code-request [state code]
  (assoc state :code code))

(defn- handle-mfa-update [state result]
  (case result
    :retry-code (assoc state :login :mfa)
    :account (assoc state :login :complete)
    (throw (Exception. (str "Unkown update recieved: " result)))))

(defn- handle-amount-update [state balances]
  (let [{minimum :debit-minimum} state]
    (if-not minimum
      (throw (Exception. (str "Missing required key: :debit-minimum"))))
    (println "BALANCES:" balances)
    (if (safe-balance? balances minimum)
      (assoc state :balances balances)
      (throw (Exception. (str "Balances aren't high enough: " balances))))))

(defn- handle-confirmation-update [state confirmation]
  (assoc state :transaction-approval confirmation))

(defn- handle-payment-update [state payment]
  (assoc state :payment payment))

;;;;; Predicate Functions
(defn logged-in? [state]
  (= :complete (:login state)))

(defn mfa? [state]
  (= :mfa (:login state)))


;;;;; Exposed functions with all the bells and whistles composed
(defn exec-start-driver [state driver]
  (assoc state :driver driver))
(def exec-login handle-login-update)
(def exec-request-code (must-have mfa? handle-code-request))
(def exec-mfa handle-mfa-update)
(def exec-get-amounts (must-have logged-in? handle-amount-update))
(def exec-confirmation (must-have :balances handle-confirmation-update))
(def exec-payment (must-have :transaction-approval handle-payment-update))
