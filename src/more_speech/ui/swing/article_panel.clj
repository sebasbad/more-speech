(ns more-speech.ui.swing.article-panel
  (:require [more-speech.user-configuration :as uconfig]
            [more-speech.nostr.events :as events]
            [more-speech.nostr.util :as util]
            [more-speech.ui.formatters :as formatters]
            [more-speech.ui.formatter-util :as f-util]
            [more-speech.ui.swing.article-tree-util :as article-tree-util]
            [more-speech.ui.swing.edit-window :as edit-window]
            [more-speech.ui.swing.ui-context :refer :all]
            [more-speech.ui.swing.util :as swing-util :refer [copy-to-clipboard]])
  (:use [seesaw core border]))

(defn bold-label [s]
  (label :text s :font (uconfig/get-bold-font)))

(defn copy-click [e]
  (when (.isPopupTrigger e)
    (let [x (.x (.getPoint e))
          y (.y (.getPoint e))
          node (.getComponent e)
          p (popup :items [(action :name "Copy" :handler (partial copy-to-clipboard (.getText node)))])]
      (.show p (to-widget e) x y))))

(defn id-click [e]
  (if (.isPopupTrigger e)
    (copy-click e)
    (article-tree-util/id-click (config e :user-data))))

(defn make-article-info-panel []
  (let [author-name-label (label :id :author-name-label)
        label-font (uconfig/get-small-font)
        author-id-label (text :id :author-id-label :editable? false :font label-font)
        created-time-label (label :id :created-time-label)
        reply-to-label (label :id :reply-to-label)
        id-label (text :id :id-label :editable? false :font label-font)
        citing-label (text :id :citing-label :editable? false :font label-font)
        subject-label (label :id :subject-label :font label-font)
        root-label (text :id :root-label :editable? false :font label-font)
        relays-popup (popup :enabled? false)
        relays-label (label :id :relays-label :user-data relays-popup)]
    (listen relays-label
            :mouse-entered (fn [e]
                             (-> relays-popup
                                 (move! :to (.getLocationOnScreen e))
                                 show!))
            :mouse-exited (fn [_e] (hide! relays-popup)))
    (listen citing-label :mouse-pressed id-click)
    (listen root-label :mouse-pressed id-click)
    (listen id-label :mouse-pressed copy-click)
    (listen author-id-label :mouse-pressed copy-click)
    (let [grid
          (grid-panel
            :columns 3
            :preferred-size [-1 :by 70]                     ;icky.
            :items [(flow-panel :align :left :items [(bold-label "Author:") author-name-label])
                    (flow-panel :align :left :items [(bold-label "Subject:") subject-label])
                    (flow-panel :align :left :items [(bold-label "pubkey:") author-id-label])
                    (flow-panel :align :left :items [(bold-label "Created at:") created-time-label])
                    (flow-panel :align :left :items [(bold-label "Reply to:") reply-to-label])
                    (flow-panel :align :left :items [(bold-label "Relays:") relays-label])
                    (flow-panel :align :left :items [(bold-label "id:") id-label])
                    (flow-panel :align :left :items [(bold-label "Citing:") citing-label])
                    (flow-panel :align :left :items [(bold-label "Root:") root-label])])]
      grid)))

(def editor-pane-stylesheet
  "<style>
    body {font-family: courier; font-style: normal; font-size: 14; font-weight: lighter;}
    a {color: #6495ED; text-decoration: none;}</style>")

(defn make-article-area []
  (editor-pane
    :content-type "text/html"
    :editable? false
    :id :article-area
    :text editor-pane-stylesheet))

(defn go-back [_e]
  (article-tree-util/go-back-by 1))

(defn go-forward [_e]
  (article-tree-util/go-back-by -1))

(defn make-control-panel []
  (let [reply-button (button :text "Reply")
        create-button (button :text "Create")
        back-button (button :text "Back")
        forward-button (button :text "Forward")]
    (listen reply-button :action
            (fn [_]
              (edit-window/make-edit-window :reply)))
    (listen create-button :action
            (fn [_] (edit-window/make-edit-window :send)))
    (listen back-button :action go-back)
    (listen forward-button :action go-forward)
    (border-panel :west back-button
                  :east forward-button
                  :center (flow-panel :items [reply-button create-button]))))

(defn load-article-info [selected-id]
  (let [main-frame (:frame @ui-context)
        text-map (get-event-state :text-event-map)
        event (get text-map selected-id)
        [root-id _ referent] (events/get-references event)
        reply-to (select main-frame [:#reply-to-label])
        citing (select main-frame [:#citing-label])
        root-label (select main-frame [:#root-label])
        relays-label (select main-frame [:#relays-label])
        relays-popup (config relays-label :user-data)
        article-area (select main-frame [:#article-area])
        subject-label (select main-frame [:#subject-label])]
    (swing-util/clear-popup relays-popup)
    (config! relays-popup :items (:relays event))
    (text! article-area (formatters/reformat-article
                          (formatters/replace-references event)))
    (text! (select main-frame [:#author-name-label])
           (formatters/format-user-id (:pubkey event) 50))
    (text! (select main-frame [:#author-id-label])
           (util/num32->hex-string (:pubkey event)))
    (text! (select main-frame [:#created-time-label])
           (f-util/format-time (:created-at event)))
    (config! (select main-frame [:#id-label])
             :user-data (:id event)
             :text (util/num32->hex-string (:id event)))
    (if (some? referent)
      (let [replied-event (get text-map referent)]
        (text! reply-to (formatters/format-user-id (:pubkey replied-event) 50))
        (config! citing
                 :user-data referent
                 :text (util/num32->hex-string referent)))
      (do (text! reply-to "")
          (text! citing "")))
    (if (some? root-id)
      (config! root-label
               :user-data root-id
               :text (util/num32->hex-string root-id))
      (text! root-label ""))
    (text! subject-label (formatters/get-subject (:tags event)))
    (text! relays-label (pr-str (count (:relays event)) (first (:relays event))))))