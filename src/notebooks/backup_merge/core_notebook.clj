^{:nextjournal.clerk/visibility {:code :hide}}
(ns notebooks.backup-merge.core-notebook
  ;; {:nextjournal.clerk/toc true}
  (:require
   [babashka.fs :as fs]
   [backup-merge.core :as bm]
   [backup-merge.notebook-utils :as nu]
   [clojure.set :as set]
   [clojure.string :as str]
   [mount.core :as mount]
   [nextjournal.clerk :as clerk]
   [nextjournal.clerk.viewer :as v]
   [xtdb.api :as xt]
   [taoensso.timbre :as log]))

;; # Backup Merge

;; [Core](../../main/backup_merge/core.clj)

{::clerk/visibility {:code :hide :result :hide}}

^{::clerk/visibility {:code :hide :result :hide}}
(def backup-files (map str (fs/list-dir bm/data-path)))

^{::clerk/sync true}
(defonce !state
  (atom
   {:backup-file-lines 5
    :backup-page       1
    :event-count       1
    :event-page        1
    :pending-loads     []
    :file-a            (first backup-files)
    :file-b            (second backup-files)
    :filters           {:pubkey nil}
    :xtdb              {:expected false
                        :actual   (bm/db-started?)}}))

(def f1 (:file-a @!state))
(def f2 (:file-b @!state))

(def events (bm/parse-file f1))

(def trimmed-events
  (let [event-count (:event-count @!state)]
    (take event-count events)))

(def trimmed-files
  (let [{:keys [backup-file-lines backup-page]} @!state]
    (->> backup-files
         (drop (* backup-page backup-file-lines))
         (take backup-file-lines)
         (map str))))

(def backup-file-button-viewer
  {:render-fn
   '(fn [p] [:button {:on-click #(swap! !state assoc-in [:file-a] p)} p])})

(def set-file-b-button-viewer
  {:render-fn
   '(fn [p] [:button {:on-click #(swap! !state assoc-in [:file-b] p)} "Set B"])})

(def load-file-button-viewer
  {:render-fn
   '(fn [p] [:button {:on-click #(swap! !state update-in [:pending-loads] conj p)} "Load"])})

(def filter-pubkey-viewer
  {:render-fn
   '(fn [p]
      [:button
       {:on-click #(swap! !state assoc-in [:filters :pubkey] p)}
       p])})

(def reset-pubkey-filter-viewer
  {:render-fn
   '(fn [p]
      [:button
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

(defn toggle-db-connection
  [state]
  (let [{{:keys [expected actual]} :xtdb} state]
    (when (not= expected actual)
      (if expected
        (if (bm/db-started?)
          (log/info "Already started")
          (mount/start))
        (if (bm/db-started?)
          (mount/stop)
          (log/info "Already stopped")))
      (swap! !state assoc-in [:xtdb :actual] expected))))

(defn process-pending-loads
  [state]
  (if (bm/db-started?)
    (do
      (log/info "Process pending loads")
      (let [[pl & r] (:pending-loads state)]
        (when pl
          (log/info "processing file" pl)
          (bm/load-file! pl)
          (swap! !state assoc-in [:pending-loads] (or r [])))))
    (log/error "Db Not started")))

(defn state-monitor
  [state]
  (log/info "Running state monitor" state)
  (toggle-db-connection state)
  (process-pending-loads state))

(defn process-file
  [f]
  (let [rows (bm/parse-file (str f))
        ids  (map #(get % "id") rows)]
    {:f    (str f)
     :c    (count rows)
     :rows (into #{} ids)}))

(defn find-event-in-file
  "Finds an event in the file"
  [file-name id]
  (let [rows (bm/parse-file file-name)]
    (first (filter #(= id (get % "id")) rows))))

(def s1 (:rows (process-file f1)))
(def s2 (:rows (process-file f2)))
(def i1 (set/intersection s1 s2))
(def target-id (first i1))

(defn number-spinner
  [path]
  (clerk/html
   [:ul
    [:li (clerk/with-viewer increase-file-counter-viewer path)]
    [:li (clerk/with-viewer decrease-file-counter-viewer path)]]))

(defn find-extra-keys
  []
  (filter
   seq
   (map
    #(dissoc %
             "content"
             "created_at"
             "id"
             "kind"
             "pubkey"
             "sig"
             "tags"
             "karma"
             "seen_on")
    (flatten
     (map
      (fn [f]
        (bm/parse-file (str f)))
      backup-files)))))

(def target-pubkey (get-in @!state [:filters :pubkey]))

(defn event-query
  []
  '(-> (from :events [{:xt/id id} event])
       #_(where (if $target-pubkey
                (= (:pubkey event) $target-pubkey)
                true))
       (limit 5)))

(def db-events
  #_[]
  (let [q (event-query)]
    (xt/q bm/node q {:args {:target-pubkey target-pubkey}})))

(comment

  (let [q '(-> (from :events [*]) (aggregate {:c (row-count)}))]
    (xt/q bm/node q))

  #_|)

(defn format-e
  [e]
  (let [{:keys [event]} e]
    [:li
     [:p "Author: "
      (clerk/with-viewer filter-pubkey-viewer
        (:pubkey event))]
     [:p (:kind event)]
     [:p (:content event)]
     [:p (str (java.sql.Timestamp. (* (:created-at event) 1000)))]
     [:p (:sig event)]
     (let [others (dissoc event :id :pubkey :kind :content :tags :created-at :sig)]
       (when (seq others)
         [:p [:code [:pre (str others)]]]))
     [:table
      [:thead
       [:tr
        [:th "tag"]
        [:th "value"]
        [:th "relay"]
        [:th "extra"]]]
      [:tbody
       (for [[tag value relays & extras] (:tags event)]
         [:tr
          [:td tag]
          [:td value]
          [:td relays]
          [:td (str/join ", " extras)]])]]]))

^{:clerk/no-cache true}
(state-monitor @!state)

{::clerk/visibility {:code :hide :result :show}}

(clerk/md
 (str "Backup Page " (:backup-page @!state) " / "
      (int (Math/ceil (/ (count backup-files) (:backup-file-lines @!state))))))
(number-spinner [:backup-page])

(clerk/md
 (str "Backup File Lines " (:backup-file-lines @!state) " / " (count backup-files)))
(number-spinner [:backup-file-lines])

^{::clerk/no-cache true}
(->> (for [p trimmed-files]
       [:tr {}
        [:td (clerk/with-viewer backup-file-button-viewer p)]
        [:td (clerk/with-viewer set-file-b-button-viewer p)]
        (when (bm/db-started?)
          [:td (clerk/with-viewer load-file-button-viewer p)])
        #_[:td "button 3"]])
     (apply vector :table)
     clerk/html)

(clerk/code @!state)

(clerk/with-viewer toggle-xtdb-state {:state !state})

^{::clerk/no-cache true}
(if (bm/db-started?)
  (clerk/code (xt/status bm/node))
  (clerk/html [:p "XTDB not started"]))

(number-spinner [:event-count])

(let [event-count (:event-count @!state)]
  (clerk/html
   [:div
    [:p "Showing " event-count  " events"]
    [:input {:type "text" :name "foo"}]
    [:div (map #(v/with-viewer nu/nostr-event-viewer %) trimmed-events)]]))

(number-spinner [:event-count])

(clerk/table
 [["In Both"   (count i1)]
  ["In File A" (count (set/difference s1 i1))]
  ["In File B" (count (set/difference s2 i1))]])

(clerk/html
 [:div
  [:hr]
  [:h3 "Events in database"]
  [:table
   [:tr
    [:td target-pubkey]
    [:td (clerk/with-viewer reset-pubkey-filter-viewer nil)]]]
  (if (bm/db-started?)
    [:ul (map format-e db-events)]
    [:p "Database not started"])
  [:hr]])

{::clerk/visibility {:code :show :result :show}}

(event-query)

^{::clerk/visibility {:code :hide :result :hide}}
(comment
  (find-event-in-file f1 target-id)
  (find-event-in-file f2 target-id)

  (clerk/show! "src/notebooks/backup_merge/core_notebook.clj")

  (xt/execute-tx bm/node (bm/insert-events trimmed-events))

  #_|)
