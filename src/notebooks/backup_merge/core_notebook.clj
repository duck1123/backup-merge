^{:nextjournal.clerk/visibility {:code :hide}}
(ns notebooks.backup-merge.core-notebook
  ;; {:nextjournal.clerk/toc true}
  (:require
   [babashka.fs :as fs]
   [backup-merge.core :as bm]
   [backup-merge.notebook-utils :as nu]
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
    :event-count       1
    :target-file       (str (first backup-files))
    :xtdb              {:expected false
                        :actual   (bm/db-started?)}}))

(def first-backup
  (or
   (:target-file @!state)
   (str (first backup-files))))

(def events (bm/parse-file first-backup))

(def trimmed-files
  (map str (take (:backup-file-lines @!state 5) backup-files)))

(def backup-file-button-viewer
  {:render-fn
   '(fn [p] [:button {:on-click #(swap! !state assoc :target-file p)} p])})

(def increase-file-counter-viewer
  {:render-fn
   '(fn []
      [:button.bg-sky-500.hover:bg-sky-700.text-white.rounded-xl.px-2.py-1
       {:on-click #(swap! !state update-in [:event-count] inc)}
       "↑"])})

(def decrease-file-counter-viewer
  {:render-fn
   '(fn []
      [:button.bg-sky-500.hover:bg-sky-700.text-white.rounded-xl.px-2.py-1
       {:on-click #(swap! !state update-in [:event-count] dec)}
       "↓"])})

(def toggle-xtdb-state
  {:render-fn
   '(fn []
      [:button.bg-sky-500.hover:bg-sky-700.text-white.rounded-xl.px-2.py-1
       {:on-click #(swap! !state update-in [:xtdb :expected] not)}
       (str "Started: " (get-in !state [:xtdb :expected]))])})

(defn state-monitor
  []
  (log/info "Running state monitor" @!state)
  (when (get-in @!state [:xtdb :expected])
    (when-not (bm/db-started?)
      (mount/start)
      (swap! !state assoc-in [:xtdb :actual] true))))

(comment

  ^{::clerk/no-cache true}
  (when (bm/db-started?)
    (xt/status bm/node))

  ^{::clerk/no-cache true}
  (mount/running-states)

  #_|)

^{:clerk/no-cache true}
(state-monitor)

{::clerk/visibility {:code :show :result :show}}

(clerk/with-viewer toggle-xtdb-state {})

^{::clerk/visibility {:code :hide :result :show}}
(->> (for [p trimmed-files]
       [:li {} (clerk/with-viewer backup-file-button-viewer p)])
     (apply vector :ul)
     clerk/html)

^{::clerk/visibility {:code :hide :result :show}}
(clerk/code @!state)

^{::clerk/visibility {:code :hide :result :show}}
(clerk/with-viewer increase-file-counter-viewer {})

^{::clerk/visibility {:code :hide :result :show}}
(clerk/with-viewer decrease-file-counter-viewer {})

^{::clerk/visibility {:code :hide :result :show}}
(let [event-count (:event-count @!state)]
  (clerk/html
   [:div
    [:p "Showing " event-count  " events"]
    [:div
     (map #(v/with-viewer nu/nostr-event-viewer %)
          (take event-count events))]]))

^{::clerk/visibility {:code :hide :result :show}}
(clerk/with-viewer increase-file-counter-viewer {})

^{::clerk/visibility {:code :hide :result :show}}
(clerk/with-viewer decrease-file-counter-viewer {})

^{::clerk/visibility {:code :hide :result :hide}}
(comment
  (mount/start)
  (mount/stop)

  (clerk/show! "src/notebooks/backup_merge/core_notebook.clj")

  (mount/current-state (str #'bm/node))
  (mount/->DerefableState (str #'bm/node))

  (xt/status bm/node)

  (with-open [conn (jdbc/get-connection bm/db)]
    (jdbc/execute! conn ["INSERT INTO users RECORDS {_id: 'jms', name: 'James'}, {_id: 'joe', name: 'Joe'}"])

    (prn (jdbc/execute! conn ["SELECT * FROM users"])))

  #_|)
