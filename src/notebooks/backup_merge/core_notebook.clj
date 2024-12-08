^{:nextjournal.clerk/visibility {:code :hide}}
(ns notebooks.backup-merge.core-notebook
  ;; {:nextjournal.clerk/toc true}
  (:require
   [babashka.fs :as fs]
   [backup-merge.core :as bm]
   ;; [cheshire.core :as json]
   ;; [clojure.java.io :as io]
   [mount.core :as mount]
   [next.jdbc :as jdbc]
   [nextjournal.clerk :as clerk]
   ;; [xtdb.client :as xtc]
   [xtdb.api :as xt]))

;; # Backup Merge

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

(comment
  (mount/start)
  (mount/stop)

  (mount/current-state (str #'bm/node))
  (mount/->DerefableState (str #'bm/node))

  (xt/status bm/node)

  (with-open [conn (jdbc/get-connection db)]
    (jdbc/execute! conn ["INSERT INTO users RECORDS {_id: 'jms', name: 'James'}, {_id: 'joe', name: 'Joe'}"])

    (prn (jdbc/execute! conn ["SELECT * FROM users"])))

  #_|)
