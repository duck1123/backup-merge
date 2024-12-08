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
   [xtdb.api :as xt]))

;; # Backup Merge

;; [Core](../../main/backup_merge/core.clj)

{::clerk/visibility {:code :hide :result :hide}}

^{::clerk/sync true}
(defonce !counter (atom 0))

^{::clerk/visibility {:code :hide :result :hide}}
(def backup-files (fs/list-dir bm/data-path))

(def first-backup (str (first backup-files)))

(def events (bm/parse-file first-backup))

{::clerk/visibility {:code :show :result :show}}

^{::clerk/visibility {:code :hide :result :show}}
(clerk/html
 [:ul (map (fn [p] [:li (str p)]) (take 5 backup-files))])

^{::clerk/no-cache true}
bm/node

^{::clerk/no-cache true}
(when (bm/db-started?)
  (xt/status bm/node))

^{::clerk/no-cache true}
(mount/running-states)

^{::clerk/visibility {:code :hide :result :show}}
(clerk/with-viewer
  {:render-fn
   '(fn []
      [:button.bg-sky-500.hover:bg-sky-700.text-white.rounded-xl.px-2.py-1
       {:on-click #(swap! !counter inc)}
       "↑"])}
  {})

^{::clerk/visibility {:code :hide :result :show}}
(clerk/with-viewer
  {:render-fn
   '(fn []
      [:button.bg-sky-500.hover:bg-sky-700.text-white.rounded-xl.px-2.py-1
       {:on-click #(swap! !counter dec)}
       "↓"])}
  {})

^{::clerk/visibility {:code :hide :result :show}}
(let [event-count @!counter]
  (clerk/html
   [:div
    [:p "Showing " @!counter  " events"]
    [:div
     (map #(v/with-viewer nu/nostr-event-viewer %)
          (take event-count events))]]))

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
