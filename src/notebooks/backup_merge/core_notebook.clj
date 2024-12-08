(ns notebooks.backup-merge.core-notebook
  (:require
   [babashka.fs :as fs]
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [next.jdbc :as jdbc]
   [nextjournal.clerk :as clerk]
   [xtdb.client :as xtc]
   [xtdb.node :as xtn]
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

  (with-open [conn (jdbc/get-connection db)]
    (jdbc/execute! conn ["INSERT INTO users RECORDS {_id: 'jms', name: 'James'}, {_id: 'joe', name: 'Joe'}"])

    (prn (jdbc/execute! conn ["SELECT * FROM users"])))

  #_|)


(comment
  (with-open [node (xtc/start-client "http://localhost:3000")]
    (xt/status node)
    ;; ...
    )
  #_|)

(comment
  (with-open [node (xtn/start-node)]
    (xt/status node)
    ;; ...
    )
  #_|)
