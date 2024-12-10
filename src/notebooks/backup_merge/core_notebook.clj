^{:nextjournal.clerk/visibility {:code :hide}}
(ns notebooks.backup-merge.core-notebook
  ;; {:nextjournal.clerk/toc true}
  (:require
   [babashka.fs :as fs]
   [backup-merge.core :as bm]
   [backup-merge.notebook-utils :as nu]
   [clojure.set :as set]
   [mount.core :as mount]
   [next.jdbc :as jdbc]
   [nextjournal.clerk :as clerk]
   [nextjournal.clerk.viewer :as v]
   [xtdb.api :as xt]
   [taoensso.timbre :as log]))

;; # Backup Merge

;; [Core](../../main/backup_merge/core.clj)

{::clerk/visibility {:code :hide :result :hide}}

^{::clerk/visibility {:code :hide :result :hide}}
(def backup-files (fs/list-dir bm/data-path))

^{::clerk/sync true}
(defonce !state
  (atom
   {:backup-file-lines 5
    :backup-page       1
    :event-count       1
    :event-page        1
    :target-file       (str (first backup-files))
    :xtdb              {:expected false
                        :actual   (bm/db-started?)}}))

(def first-backup (:target-file @!state))

(def events (bm/parse-file first-backup))

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
   '(fn [p] [:button {:on-click #(swap! !state assoc :target-file p)} p])})

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

(defn state-monitor
  []
  (log/info "Running state monitor" @!state)
  (let [{{:keys [expected actual]} :xtdb} @!state]
    (when (not= expected actual)
      (if expected
        (if (bm/db-started?)
          (log/info "Already started")
          (mount/start))
        (if (bm/db-started?)
          (mount/stop)
          (log/info "Already stopped")))
      (swap! !state assoc-in [:xtdb :actual] expected))))

^{:clerk/no-cache true}
(state-monitor)

(defn process-file
  [f]
  (let [rows (bm/parse-file (str f))
        ids  (map #(get % "id") rows)]
    {:f    (str f)
     :c    (count rows)
     :rows (into #{} ids)}))

(defn find-event
  [file-name id]
  (let [rows (bm/parse-file (str file-name))]
    (filter (partial #{id})  rows)))

(def f1 (first backup-files))
(def f2 (second backup-files))

(def s1 (:rows (process-file f1)))
(def s2 (:rows (process-file f2)))

{::clerk/visibility {:code :show :result :show}}

#_
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
    backup-files))))

^{::clerk/visibility {:code :hide :result :show}}
(->> (for [p trimmed-files]
       [:li {} (clerk/with-viewer backup-file-button-viewer p)])
     (apply vector :ul)
     clerk/html)

^{::clerk/visibility {:code :hide :result :show}}
(clerk/code @!state)

^{::clerk/visibility {:code :hide :result :show}}
(clerk/with-viewer toggle-xtdb-state {:state !state})

 ^{::clerk/no-cache true}
(when (bm/db-started?)
  (xt/status bm/node))

^{::clerk/visibility {:code :hide :result :show}}
(clerk/html
 [:ul
  [:li (clerk/with-viewer increase-file-counter-viewer [:event-count])]
  [:li (clerk/with-viewer decrease-file-counter-viewer [:event-count])]])

^{::clerk/visibility {:code :hide :result :show}}
(let [event-count (:event-count @!state)]
  (clerk/html
   [:div
    [:p "Showing " event-count  " events"]
    [:input {:type "text" :name "foo"}]
    [:div (map #(v/with-viewer nu/nostr-event-viewer %) trimmed-events)]]))

^{::clerk/visibility {:code :hide :result :show}}
(clerk/html
 [:ul
  [:li (clerk/with-viewer increase-file-counter-viewer [:event-count])]
  [:li (clerk/with-viewer decrease-file-counter-viewer [:event-count])]])

(flatten (map process-file backup-files))

(set/difference s1 s2)

^{::clerk/visibility {:code :hide :result :show}}
(clerk/table
 [["in both" (count (set/intersection s1 s2))]
  ["in a" (count (set/difference s1 (set/intersection s1 s2)))]
  ["in b" (count (set/difference s2 (set/intersection s1 s2)))]])

^{::clerk/no-cache true}
(when (bm/db-started?)
  (xt/q bm/node '(from :events [*])))


(def q
  [(into [:put-docs {:into :events}]
         (for [event trimmed-events]
           {:xt/id (get event "id") :event event}))])

^{::clerk/visibility {:code :hide :result :hide}}
(comment
  (mount/start)
  (mount/stop)

  (clerk/show! "src/notebooks/backup_merge/core_notebook.clj")

  (xt/execute-tx bm/node q)

  (mount/current-state (str #'bm/node))
  (mount/->DerefableState (str #'bm/node))

  (xt/status bm/node)

  (with-open [conn (jdbc/get-connection bm/db)]
    (jdbc/execute! conn ["INSERT INTO users RECORDS {_id: 'jms', name: 'James'}, {_id: 'joe', name: 'Joe'}"])

    (prn (jdbc/execute! conn ["SELECT * FROM users"])))

  #_|)
