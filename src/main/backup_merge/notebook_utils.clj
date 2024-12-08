(ns backup-merge.notebook-utils
  (:require
   [nextjournal.clerk :as clerk]))

(def greet-viewer
  {:transform-fn (clerk/update-val #(clerk/html [:strong "Hello, " % " 👋"]))})

(def nostr-event-viewer
  {:transform-fn
   (clerk/update-val
    (fn [event]
      (clerk/html
        (let [{id      "id"
               pubkey  "pubkey"
               kind    "kind"
               content "content"} event]
          [:div {}
           [:p "Id: " id]
           [:p "Pubkey: " pubkey]
           [:p "kind: " kind]
           [:p "Content: " content]
           [:pre
            [:code (str (dissoc event "id" "pubkey" "kind" "content"))]]])
)))})
