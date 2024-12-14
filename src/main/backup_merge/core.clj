(ns backup-merge.core
  (:require
   [babashka.fs :as fs]
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [mount.core :as mount :refer [defstate]]
   [nextjournal.clerk :as clerk]
   [nrepl.server :as nrepl]
   [taoensso.timbre :as log]
   [xtdb.api :as xt]
   [xtdb.node :as xtn]))

;; [Notebook](../../notebooks/backup_merge/core_notebook.clj)

(def xtdb-opts
  {:log     [:local {:path "tx-log"}]
   :storage [:local {:path "storage"}]})

(defstate ^{:on-reload :noop} node
  :start
  (do
    (log/info "starting")
    (xtn/start-node xtdb-opts))
  :stop
  (do
    (log/info "stopping")
    (let [{:keys [close-fn]} node]
      (close-fn))))

(defn db-started?
  []
  (.isRealized (mount/->DerefableState (str #'node))))

(def db
  {:dbtype "postgresql"
   :dbname "xtdb"
   :user "xtdb"
   :password "xtdb"
   :host "localhost"
   :port 5432})

(def data-path (fs/absolutize (fs/path "data")))

(defn nrepl-handler []
  (require 'cider.nrepl)
  (ns-resolve 'cider.nrepl 'cider-nrepl-handler))

(defn start-services!
  [options]
  (when-let [clerk-port (::clerk-port options)]
    (clerk/serve!
     {:host           "0.0.0.0"
      :port           (Integer/parseInt clerk-port)
      :show-filter-fn #(str/starts-with? % "src/notebooks")
      :watch-paths    ["src/main" "src/notebooks"]}))

  (when-let [nrepl-port (::nrepl-port options)]
    (log/infof "starting server on %s:%s" "0.0.0.0" nrepl-port)
    (nrepl/start-server
     :port (Integer/parseInt nrepl-port)
     :bind "0.0.0.0"
     :handler (nrepl-handler))))

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

(defn insert-events
  [events]
  [(into [:put-docs {:into :events}]
         (for [event events]
           {:xt/id (get event "id") :event event}))])

(defn load-file!
  [file-name]
  (let [rows (parse-file file-name)]
    (xt/execute-tx node (insert-events rows))))

(defn clerk-command
  [& [args]]
  (log/info "args" args)
  (start-services! {::clerk-port "7777" ::nrepl-port "7000"})
  (loop []
    (Thread/sleep (* 3600 1000))
    (recur)))

(defn -main
  [& args1]
  (prn args1)
  (prn *command-line-args*)
  (let [[args] args1]
    (println "args" args)))
