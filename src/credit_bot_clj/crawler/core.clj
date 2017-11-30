(ns credit-bot-clj.crawler.core
  (:require [etaoin.api :refer [boot-driver quit headless]]
            [credit-bot-clj.crawler.actions :as actions]
            [credit-bot-clj.crawler.state :as S]
            [credit-bot-clj.utils :as util :refer [req req-res]]
            [clojure.core.async :as async :refer [chan >!! <!! >! <! take! put!]]
            [clojure.tools.logging :as log]))

(defn- exec-action [state-updater action!]
  "This awesome function pairs actions and state manipulation"
  (fn [state]
    (swap! state state-updater (action! @state))))

(defn- exec-req [state-updater action!]
  "Similar to exec-action, although note that the request doesn't take state as a parameter,
  it in fact takes no arguments at all"
  (fn [state]
    (swap! state state-updater (action!))))


(defn make-crawler
  ([credentials]
    (make-crawler credentials {:debug? false}))
  ([credentials opts]
    (let [;; Channels
          start-req (chan 1)
          finish-req (chan 1)
          mfa-code-res (chan 1)
          mfa-code-req (chan 1)
          status-out (chan 1)
          amount-out (chan 1)
          transaction-req (chan 1)
          transaction-res (chan 1)

          ;; Requests
          request-mfa-code #(req-res mfa-code-req mfa-code-res)
          request-confirmation #(req-res transaction-req transaction-res)
          request-finish #(req finish-req)

          ;; Composed Functions
          start-driver! (exec-action S/exec-start-driver
                                     actions/start-driver!)
          attempt-login! (exec-action S/exec-login
                                      actions/login!)
          request-code! (exec-req S/exec-request-code
                                     request-mfa-code)
          attempt-mfa-login! (exec-action S/exec-mfa
                                          actions/login-with-code!)
          get-amounts! (exec-action S/exec-get-amounts
                                    actions/get-amounts!)
          request-confirmation! (exec-req S/exec-confirmation
                                             request-confirmation)
          pay! (exec-action S/exec-payment
                            actions/pay!)

          ;; Etc
          init-state (merge opts
                            {:started false
                             :credentials credentials})
          state (atom init-state)]

      (async/go-loop []
        (<! start-req) ; Wait until we get a start signal
        (log/info "STARTING CRAWLER")
        (log/info @state)
        (doto state
              start-driver!
              attempt-login!)
        (while (not (S/logged-in? @state))
          (doto state
                request-code!
                attempt-mfa-login!))
        (doto state
              get-amounts!
              request-confirmation!
              pay!)
        (quit (:driver @state))
        (reset! state init-state)
        (log/info "Got over to here")
        (request-finish)
        (recur))
      {:start-req start-req
       :finish-req finish-req
       :transaction-req transaction-req
       :transaction-res transaction-res
       :mfa-code-res mfa-code-res
       :mfa-code-req mfa-code-req
       :state state})))
