^{:nextjournal.clerk/visibility {:code :hide}}
(ns notebooks.backup-merge.core-notebook
  ;; {:nextjournal.clerk/toc true}
  (:require
   [babashka.fs :as fs]
   [backup-merge.core :as bm]
   [backup-merge.notebook-utils :as nu]
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [mount.core :as mount]
   [next.jdbc :as jdbc]
   [nextjournal.clerk :as clerk]
   [nextjournal.clerk.viewer :as v]
   ;; [xtdb.client :as xtc]
   [xtdb.api :as xt]))

;; # Backup Merge

;; [Core](../../main/backup_merge/core.clj)

{::clerk/visibility {:code :hide :result :hide}}

(def data-path (fs/absolutize (fs/path "data")))

^{::clerk/visibility {:code :hide :result :hide}}
(def backup-files (fs/list-dir data-path))

(def db
  {:dbtype "postgresql"
   :dbname "xtdb"
   :user "xtdb"
   :password "xtdb"
   :host "localhost"
   :port 5432})

(defn parse-file
  [file-name]
  (let [reader (io/reader file-name)
        events (json/parsed-seq reader)]
    events))

(defn merge-jsonl
  [& [args]]
  (doseq [in (:_arguments args)]
    (let [reader (io/reader in)
          events (json/parsed-seq reader)]
      (println (str "|" in "|" (count events) "|"))
      #_(doseq [v (take 5 events)]
          (println (get v "id"))))))

(def first-backup (str (first backup-files)))

(def events (parse-file first-backup))

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

(v/with-viewer nu/nostr-event-viewer (first events))

^{::clerk/visibility {:code :hide :result :hide}}
(comment
  (mount/start)
  (mount/stop)

  (clerk/show! "src/notebooks/backup_merge/core_notebook.clj")

  (mount/current-state (str #'bm/node))
  (mount/->DerefableState (str #'bm/node))

  (xt/status bm/node)

  (with-open [conn (jdbc/get-connection db)]
    (jdbc/execute! conn ["INSERT INTO users RECORDS {_id: 'jms', name: 'James'}, {_id: 'joe', name: 'Joe'}"])

    (prn (jdbc/execute! conn ["SELECT * FROM users"])))

  #_|)
