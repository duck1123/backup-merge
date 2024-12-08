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
(def backup-files (map str (fs/list-dir bm/data-path)))

^{::clerk/sync true}
(defonce !state
  (atom
   {:backup-file-lines 5
    :backup-page       1
    :event-count       1
    :event-page        1
    ;; :target-file       (first backup-files)
    :file-a            (first backup-files)
    :file-b            (second backup-files)
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

(def load-file-button-viewer
  {:render-fn
   '(fn [p] [:button {:on-click #(swap! !state assoc-in [:file-a] p)} "Load"])})

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
  [state]
  (log/info "Running state monitor" state)
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

(defn process-file
  [f]
  (let [rows (bm/parse-file (str f))
        ids  (map #(get % "id") rows)]
    {:f    (str f)
     :c    (count rows)
     :rows (into #{} ids)}))

(defn find-event
  [file-name id]
  (let [rows (bm/parse-file file-name)]
    (first (filter #(= id (get % "id")) rows))))

(def s1 (:rows (process-file f1)))
(def s2 (:rows (process-file f2)))
(def target-id (first (set/intersection s1 s2)))

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

^{:clerk/no-cache true}
(state-monitor @!state)

{::clerk/visibility {:code :show :result :show}}

{::clerk/visibility {:code :hide :result :show}}

(clerk/md (str "Backup Page " (:backup-page @!state)))
(number-spinner [:backup-page])

(clerk/md (str "Backup File Lines " (:backup-file-lines @!state)))
(number-spinner [:backup-file-lines])

(->> (for [p trimmed-files]
       [:tr {}
        [:td (clerk/with-viewer backup-file-button-viewer p)]
        [:td (clerk/with-viewer load-file-button-viewer p)]
        [:td "button 3"]])
     (apply vector :table)
     clerk/html)

{::clerk/visibility {:code :show :result :show}}

(find-event f1 target-id)
(find-event f2 target-id)

^{::clerk/visibility {:code :hide :result :show}}
(clerk/code @!state)

^{::clerk/visibility {:code :hide :result :show}}
(clerk/with-viewer toggle-xtdb-state {:state !state})

 ^{::clerk/no-cache true}
(when (bm/db-started?)
  (xt/status bm/node))

^{::clerk/visibility {:code :hide :result :show}}
(number-spinner [:event-count])

^{::clerk/visibility {:code :hide :result :show}}
(let [event-count (:event-count @!state)]
  (clerk/html
   [:div
    [:p "Showing " event-count  " events"]
    [:input {:type "text" :name "foo"}]
    [:div (map #(v/with-viewer nu/nostr-event-viewer %) trimmed-events)]]))

^{::clerk/visibility {:code :hide :result :show}}
(number-spinner [:event-count])

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

^{::clerk/visibility {:code :hide :result :hide}}
(comment
  (mount/start)
  (mount/stop)

  (clerk/show! "src/notebooks/backup_merge/core_notebook.clj")

  (xt/execute-tx bm/node (bm/insert-events trimmed-events))

  (mount/current-state (str #'bm/node))
  (mount/->DerefableState (str #'bm/node))

  (xt/status bm/node)

  (with-open [conn (jdbc/get-connection bm/db)]
    (jdbc/execute! conn ["INSERT INTO users RECORDS {_id: 'jms', name: 'James'}, {_id: 'joe', name: 'Joe'}"])

    (prn (jdbc/execute! conn ["SELECT * FROM users"])))

  #_|)
