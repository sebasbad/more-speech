(ns more-speech.ui.swing.main-window
  (:require [clojure.core.async :as async]
            [more-speech.nostr.events :as events]
            [more-speech.ui.swing.article-tree :as article-tree]
            [more-speech.ui.swing.article-panel :as article-panel]
            [more-speech.nostr.protocol :as protocol]
            [more-speech.ui.swing.ui-context :refer :all])
  (:use [seesaw core])
  (:import (javax.swing Timer)))

(defrecord seesawHandler []
  events/event-handler
  (events/handle-text-event [_handler event]
    (invoke-later (article-tree/add-event ui-context event))))

(declare make-main-window)

(defn setup-main-window [event-agent]
  (invoke-now (make-main-window event-agent))
  (->seesawHandler))

(declare make-relay-panel
         timer-action)

(defn make-main-window [event-agent]
  (let [main-frame (frame :title "More Speech" :size [1000 :by 1000])
        article-area (article-panel/make-article-area)
        header-tree (article-tree/make-article-tree event-agent main-frame)
        relay-panel (make-relay-panel)
        header-panel (left-right-split (scrollable relay-panel)
                                       (scrollable header-tree))
        article-panel (border-panel :north (article-panel/make-article-info-panel)
                                    :center (scrollable article-area)
                                    :south (article-panel/make-control-panel event-agent header-tree))
        main-panel (top-bottom-split
                     header-panel
                     article-panel)
        timer (Timer. 100 nil)]
    (config! main-frame :content main-panel)
    (swap! ui-context assoc :frame main-frame :event-agent event-agent)

    (listen timer :action timer-action)

    (listen main-frame :window-closing
            (fn [_]
              (.stop timer)
              (async/>!! (:send-chan @event-agent)
                         [:closed])
              (.dispose main-frame)))

    (show! main-frame)
    (.start timer)))

(defn make-relay-control-panel [relay]
  (let [read-check-box (checkbox :text "R")
        write-check-box (checkbox :text "W")
        relay-label (flow-panel :align :left
                                :items [(label :text relay :halign :left)])
        buttons (flow-panel :align :left
                            :items [read-check-box write-check-box])
        control-panel (vertical-panel :items [relay-label buttons (separator :border 1)])]
    control-panel))

(defn make-relay-panel []
  (let [relay-control-panels (for [relay protocol/relays] (make-relay-control-panel relay))
        relay-panel (vertical-panel :items relay-control-panels)]
    relay-panel))

(defn timer-action [_]
  ;nothing for now.
  )


