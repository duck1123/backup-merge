(ns notebooks.backup-merge.core-notebook
  (:require
   [babashka.fs :as fs]
   [backup-merge.core :as bm]
   ;; [cheshire.core :as json]
   ;; [clojure.java.io :as io]
   [next.jdbc :as jdbc]
   [nextjournal.clerk :as clerk]
   ;; [xtdb.client :as xtc]
   [xtdb.api :as xt]))

;; # Backup Merge

(def data-path (fs/absolutize (fs/path "data")))

(fs/cwd)

(def backup-files (fs/list-dir data-path))

(clerk/html
 [:ul (map (fn [p] [:li (str p)]) backup-files)])

(def db
  {:dbtype "postgresql"
   :dbname "xtdb"
   :user "xtdb"
   :password "xtdb"
   :host "localhost"
   :port 5432})

(comment
  (xt/status bm/node)

  (with-open [conn (jdbc/get-connection db)]
    (jdbc/execute! conn ["INSERT INTO users RECORDS {_id: 'jms', name: 'James'}, {_id: 'joe', name: 'Joe'}"])

    (prn (jdbc/execute! conn ["SELECT * FROM users"])))

  #_|)
