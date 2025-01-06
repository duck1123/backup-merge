^{:nextjournal.clerk/visibility {:code :hide}}
(ns backup-merge.core-notebook
  ;; {:nextjournal.clerk/toc true}
  (:require
   [babashka.fs :as fs]
   [backup-merge.core :as bm]
   [backup-merge.notebook-utils :as nu]
   [clojure.set :as set]
   [clojure.string :as str]
   [nextjournal.clerk :as clerk]
   [nextjournal.clerk.viewer :as v]
   [xtdb.api :as xt]))

;; # Backup Merge

;; [Core](../../main/backup_merge/core.clj)

{::clerk/visibility {:code :hide :result :hide}}

(def backup-files (bm/get-backup-files))

^{::clerk/sync true}
(defonce !state
  (atom
   {:backup-file-lines 33
    :backup-page       1
    :event-count       1
    :event-page        1
    :pending-loads     []
    :file-a            (first backup-files)
    :file-b            (second backup-files)
    :filters           {:event  nil
                        :kind   nil
                        :pubkey nil}
    :xtdb              {:expected false
                        :actual   (bm/db-started?)}}))

(def f1 (:file-a @!state))
(def f2 (:file-b @!state))

(def trimmed-events (bm/get-trimmed-events !state f1))
(def trimmed-files (bm/get-trimmed-files !state backup-files))

(def set-file-a-button-viewer
  {:render-fn
   '(fn [p] [:button.bg-sky-500.hover:bg-sky-700.text-white.rounded-xl.px-2.py-1
             {:on-click #(swap! !state assoc-in [:file-a] p)} "Set A"])})

(def set-file-b-button-viewer
  {:render-fn
   '(fn [p] [:button.bg-sky-500.hover:bg-sky-700.text-white.rounded-xl.px-2.py-1
             {:on-click #(swap! !state assoc-in [:file-b] p)} "Set B"])})

(def load-file-button-viewer
  {:render-fn
   '(fn [p] [:button.bg-sky-500.hover:bg-sky-700.text-white.rounded-xl.px-2.py-1
             {:on-click #(swap! !state update-in [:pending-loads] conj p)} "Load"])})

(def filter-event-viewer
  {:render-fn
   '(fn [value]
      [:button.bg-sky-500.hover:bg-sky-700.text-white.rounded-xl.px-2.py-1
       {:on-click #(swap! !state assoc-in [:filters :event] value)}
       value])})

(def filter-pubkey-viewer
  {:render-fn
   '(fn [value]
      [:button.bg-sky-500.hover:bg-sky-700.text-white.rounded-xl.px-2.py-1
       {:on-click #(swap! !state assoc-in [:filters :pubkey] value)}
       value])})

(def reset-event-filter-viewer
  {:render-fn
   '(fn [_value]
      [:button.bg-sky-500.hover:bg-sky-700.text-white.rounded-xl.px-2.py-1
       {:on-click #(swap! !state assoc-in [:filters :event] nil)}
       "Reset Event"])})

(def reset-pubkey-filter-viewer
  {:render-fn
   '(fn [p]
      [:button.bg-sky-500.hover:bg-sky-700.text-white.rounded-xl.px-2.py-1
       {:on-click #(swap! !state assoc-in [:filters :pubkey] nil)}
       "Reset Pubkey"])})

(def increase-file-counter-viewer
  {:render-fn
   '(fn [path-obj]
      (let [path (into [] (map :nextjournal/value path-obj))]
        [:button.bg-sky-500.hover:bg-sky-700.text-white.rounded-xl.px-2.py-1
         {:on-click #(swap! !state update-in path inc)}
         "↑"]))})

(def decrease-file-counter-viewer
  {:render-fn
   '(fn [path-obj]
      (let [path (into [] (map :nextjournal/value path-obj))]
        [:button.bg-sky-500.hover:bg-sky-700.text-white.rounded-xl.px-2.py-1
         {:on-click #(swap! !state update-in path dec)}
         "↓"]))})

(def toggle-xtdb-state
  {:render-fn
   '(let [path [:xtdb :expected]]
      (fn []
        [:button.bg-sky-500.hover:bg-sky-700.text-white.rounded-xl.px-2.py-1
         {:on-click #(swap! !state update-in path not)}
         (if (get-in @!state path) "stop" "start")]))})

(def s1 (:rows (bm/process-file f1)))
(def s2 (:rows (bm/process-file f2)))
(def i1 (set/intersection s1 s2))
(def target-id (first i1))

(defn number-spinner
  "Displays a spinner for adjusting a value at path"
  [path]
  [:ul
   [:li (clerk/with-viewer increase-file-counter-viewer path)]
   [:li (clerk/with-viewer decrease-file-counter-viewer path)]])

(def target-event (get-in @!state [:filters :event]))
(def target-pubkey (get-in @!state [:filters :pubkey]))

(defn format-e
  [event]
  (let [{:keys [content created-at
                kind pubkey sig tags]} event
        used-keys                      #{:id :pubkey :kind :content :tags :created-at :sig :xt/id}
        others                         (apply dissoc event used-keys)]
    [:li
     [:p "Author: "
      (clerk/with-viewer filter-pubkey-viewer pubkey)]
     [:p kind]
     [:p content]
     [:p (str (java.sql.Timestamp. (* created-at 1000)))]
     [:p sig]
     (when (seq others)
       [:p [:code [:pre (str others)]]])
     [:table
      [:thead
       [:tr
        [:th "tag"]
        [:th "value"]
        [:th "relay"]
        [:th "extra"]]]
      [:tbody
       (for [[tag value relays & extras] tags]
         [:tr
          [:td tag]
          [:td
           (case tag
             "p" (clerk/with-viewer filter-pubkey-viewer value)
             "e" (clerk/with-viewer filter-event-viewer value)
             value)]
          [:td relays]
          [:td (str/join ", " extras)]])]]]))

(defn format-e1
  [e]
  [:div (clerk/with-viewers clerk/default-viewers e)]
  #_[:pre [:code (str e)]])

(defn db-viewer
  []
  (let [started?    (bm/db-started?)
        event-count (bm/count-all target-pubkey)
        db-events   (bm/get-db-events target-event target-pubkey)]
    [:div
     [:hr]
     [:h3 "Events in database: " event-count]
     [:table
      [:tr
       [:td target-pubkey]
       [:td (clerk/with-viewer reset-pubkey-filter-viewer nil)]]
      [:tr
       [:td target-event]
       [:td (clerk/with-viewer reset-event-filter-viewer nil)]]]
     (if started?
       [:ul (map format-e db-events)]
       [:p "Database not started"])]))

(defn file-viewer
  []
  (let [event-count (:event-count @!state)]
    [:div
     [:p "Showing " event-count  " events"]
     [:input {:type "text" :name "foo"}]
     [:div (map #(v/with-viewer nu/nostr-event-viewer %) trimmed-events)]]))

(defn file-diff
  []
  [["In Both"   (count i1)]
   ["In File A" (count (set/difference s1 i1))]
   ["In File B" (count (set/difference s2 i1))]])

(defn file-picker
  []
  (->> (for [p trimmed-files]
         [:tr {}
          [:td (str (fs/relativize bm/data-path p))]
          [:td (clerk/with-viewer set-file-a-button-viewer p)]
          [:td (clerk/with-viewer set-file-b-button-viewer p)]
          (when (bm/db-started?)
            [:td (clerk/with-viewer load-file-button-viewer p)])])
       (apply vector :table)))

(defn pagination-controls
  []
  [:div.border-red.border-1
   [:div {}
    [:div {} (str "Backup Page " (:backup-page @!state) " / "
                  (int (Math/ceil (/ (count backup-files) (:backup-file-lines @!state)))))]
    [:div {} (number-spinner [:backup-page])]]
   [:div {}
    [:div {} (str "Backup File Lines " (:backup-file-lines @!state) " / " (count backup-files))]
    [:div {} (number-spinner [:backup-file-lines])]]])

^{:clerk/no-cache true}
(bm/state-monitor !state @!state)

{::clerk/visibility {:code :hide :result :show}}

(clerk/with-viewer toggle-xtdb-state {:state !state})

(clerk/html (pagination-controls))

^{::clerk/no-cache true}
(clerk/html (file-picker))

(clerk/code @!state)

(clerk/table (file-diff))

^{::clerk/no-cache true}
(clerk/html (db-viewer))

^{::clerk/visibility {:code :hide :result :hide}}
(comment

  (bm/event-query)

  (bm/get-db-events target-event target-pubkey)

  (let [q '(-> (from :events [*]) (aggregate {:c (row-count)}))]
    (xt/q bm/node q))

  "33f1453db8737237a39b584c8eb20345cc391d54ad81cf91dc0715ad574812ed"

  (bm/get-tags)

  (xt/q bm/node
        '(-> (from :tags [*])
             (where (= tag "e"))
             (limit 5)
             (order-by position)
             ))

  (xt/q bm/node
        '(unify
          (from :events [{:xt/id event-id #_#_:tags ts} content sig])
          (join (-> (from :events [{:xt/id event-id} tags])
                    (unnest {:tag tags})
                    (unnest {:tag2 tag})
                    (where (= tag2 "e"))
                    (unnest {:value tag})
                    (where (= value "33f1453db8737237a39b584c8eb20345cc391d54ad81cf91dc0715ad574812ed"))
                    (limit 10))
                [event-id #_tags #_content])))

  (xt/q bm/node
        '(-> (from :events [{:xt/id event-id} content *])
             (limit 10)))

  (bm/purge-db!)

  (bm/all-ids)

  (bm/find-event-in-file f1 target-id)
  (bm/find-event-in-file f2 target-id)

  (clerk/show! "src/notebooks/backup_merge/core_notebook.clj")

  (xt/execute-tx bm/node (bm/insert-events trimmed-events))

  #_|)
