(ns credit-bot-clj.crawler.core
  (:require [etaoin.api :refer [boot-driver quit headless]]
            [credit-bot-clj.crawler.actions :as actions]
            [credit-bot-clj.crawler.state :as S]
            [credit-bot-clj.utils :as util :refer [req req-res]]
            [clojure.core.async :as async :refer [chan >!! <!! >! <! take! put!]]
            [clojure.tools.logging :as log]))

(defn start-driver! [debug?]
  (if debug?
    (boot-driver :chrome {:path "chromedriver.exe"})
    (headless)))


; TODO: double check that this function is written properly now
(defn exec-action [state-updater action!]
  "This awesome function pairs actions and state manipulation"
  (fn [state]
    (swap! state state-updater (action! state))))


(defn make-crawler
  ([credentials]
    (make-crawler credentials {:debug? false}))
  ([user credentials opts]
    (let [;; Channels
          start-req (chan 1)
          finish-res (chan 1)
          mfa-code-res (chan 1)
          mfa-code-req (chan 1)
          status-out (chan 1)
          amount-out (chan 1)
          transaction-req (chan 1)
          transaction-res (chan 1)

          ;; Requests
          request-mfa-code (req-res mfa-code-req mfa-code-res)
          request-confirmation (req-res transaction-req transaction-res)

          ;; Composed Functions
          start-driver! (exec-action S/exec-start-driver
                                     actions/start-driver!)
          attempt-login! (exec-action S/exec-login action
                                      actions/login!)
          request-code! (exec-action S/exec-request-code
                                     request-mfa-code)
          attempt-mfa-login! (exec-action S/exec-mfa
                                          actions/login-with-code!)
          get-amounts! (exec-action S/exec-get-amounts
                                    actions/get-amounts!)
          request-confirmation! (exec-action S/exec-confirmation
                                             request-confirmation)
          pay! (exec-action S/exec-payment
                            actions/pay!)

          ;; Etc
          init-state (merge opts
                            {:started false
                             :credentials credentials})
          state (atom init-state)]

      (async/go-loop []
        (log/info "STARTING CRAWLER")
        (doto state
              start-driver!
              attempt-login!)
        (while (not= (S/logged-in? @state))
          (doto state
                request-code!
                attempt-mfa!))
        (doto state
              get-amounts!
              request-confirmation!
              pay!)
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
