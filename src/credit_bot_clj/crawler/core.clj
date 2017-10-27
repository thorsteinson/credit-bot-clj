(ns credit-bot-clj.crawler.core
  (:require [etaoin.api :refer [boot-driver quit headless]]
            [credit-bot-clj.crawler.actions :as actions]
            [clojure.core.async :as async :refer [chan >!! <!! >! <! take! put!]]
            [clojure.tools.logging :as log]))

(defn start-driver! [debug?]
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
        (swap! state assoc :driver (start-driver! (:debug? opts)))
        (actions/login! (:driver @state) user password)
        (while (actions/mfa-page?! (:driver @state))
          (do
            (>! mfa-code-req :req)
            (log/info "REQUESTED MFA")
            (actions/enter-mfa-code! (:driver @state) (<! mfa-code-res))))
        (actions/nav-to-credit! (:driver @state))
        (swap! state assoc :payment (actions/extract-amounts! (:driver @state)))
        (>! transaction-req :trans-req) 
        (log/info "Waiting for transaction approval response")
        (<! transaction-res) 
        (actions/exec-payment! (:driver @state)
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
