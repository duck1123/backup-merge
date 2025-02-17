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
   [xtdb.node :as xtn])
  (:gen-class))

;; [Notebook](../../notebooks/backup_merge/core_notebook.clj)

(def base-path "../../org-roam/")

(def ?timestamp
  [:map {:closed true}
   [:active :boolean]
   [:date :time/datetime]])

(def ?entry
  [:map {:closed true}
   [:headline :string]
   [:todo :string]
   [:tags [:every :string]]
   [:scheduled ::timestamp]
   [:closed ::timestamp]
   [:priority :string]])

(def ?page
  [:map {:closed true}
   [:id :string]
   [:title :string]
   [:entries [:every ::entry]]])

(defn parse-date
  [date]
  (fs/path base-path "daily" (str date ".org")))

(comment

  base-path

  (parse-date "2022-03-26")

  #_|)

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
  (let [ds ^clojure.lang.IPending (mount/->DerefableState (str #'node))]
    (.isRealized ds)))

(clerk/example
 (db-started?))

(def db
  {:dbtype "postgresql"
   :dbname "xtdb"
   :user "xtdb"
   :password "xtdb"
   :host "localhost"
   :port 5432})

(def data-path (fs/absolutize (fs/path "data")))

(def org-data-path (fs/absolutize (fs/path base-path)))
(def org-daily-data-path (fs/absolutize (fs/path base-path "daily")))

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
        events (json/parsed-seq reader keyword)]
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
           (let [id (get event "id")]
             (assoc event :xt/id id))))])

(defn load-file!
  [file-name]
  (log/info "Loading file" file-name)
  (let [rows                      (take 5 (parse-file file-name))
        pairs                     (for [{:keys [id tags] :as event} rows]
                                    (let [tag-docs (map-indexed
                                                    (fn [position tag]
                                                      (let [[tag-key value & others] tag]
                                                        {:xt/id    (str id ":" position)
                                                         :event-id id
                                                         :position position
                                                         :tag      tag-key
                                                         :value    value
                                                         :others   others}))
                                                    tags)]
                                      {:tag-docs tag-docs :events [(assoc
                                                                    (dissoc event :tags)
                                                                    :xt/id id)]}))
        {:keys [events tag-docs]} (reduce
                                   (fn [acc i]
                                     {:tag-docs (mapcat :tag-docs [acc i])
                                      :events   (mapcat :events [acc i])})
                                   {:tag-docs [] :events []}
                                   pairs)
        stmts                     [(into [:put-docs {:into :tags}] tag-docs)
                                   (into [:put-docs {:into :events}] events)]]
    (log/info "stmts" stmts)
    (xt/execute-tx node stmts)))

(defn all-ids
  []
  (map :id (xt/q node '(from :events [{:xt/id id}]))))

(clerk/example
 (all-ids))

(defn purge-db!
  []
  (let [q [(into [:erase-docs :events] (all-ids))]]
    (xt/execute-tx node q)
    #_q))

(defn event-query
  []
  '(-> (from :events [*])
       (where
        (or (nil? $target-pubkey) (= pubkey $target-pubkey))
        (or (nil? $target-event)
            (= id $target-event)))
       (limit 5)))

(defn get-db-events
  [target-event target-pubkey]
  (if (db-started?)
    (let [q (event-query)]
      (xt/q node q
            {:args {:target-event  target-event
                    :target-pubkey target-pubkey}}))
    []))

(defn get-tags
  []
  (if (db-started?)

    (xt/q node '(-> (from :tags [*])
                    (limit 5)))
    []))

(defn get-trimmed-files
  [!state backup-files]
  (let [{:keys [backup-file-lines backup-page]} @!state]
    (->> backup-files
         (drop (* (dec backup-page) backup-file-lines))
         (take backup-file-lines)
         (map str))))

(defn get-trimmed-events
  [!state f1]
  (let [events (parse-file f1)
        event-count (:event-count @!state)]
    (take event-count events)))

(defn get-backup-files
  []
  (map str (fs/list-dir data-path)))

(defn get-org-files
  []
  (map fs/canonicalize (fs/list-dir org-data-path)))

(defn get-org-daily-files
  []
  (map fs/canonicalize (fs/list-dir org-daily-data-path)))

(defn toggle-db-connection
  [!state _state]
  (let [{{:keys [expected actual]} :xtdb} @!state
        started? (db-started?)]
    (when (not= expected actual)
      (if expected
        (if started?
          (log/info "Already started")
          (mount/start))
        (if started?
          (mount/stop)
          (log/info "Already stopped")))
      (swap! !state assoc-in [:xtdb :actual] expected))))

(defn process-pending-loads
  [!state]
  (if (db-started?)
    (do
      (log/info "Process pending loads")
      (let [[pl & r] (:pending-loads @!state)]
        (when pl
          (log/info "processing file" pl)
          (load-file! pl)
          (swap! !state assoc-in [:pending-loads] (or r [])))))
    (log/error "Db Not started")))

(defn state-monitor
  [!state state]
  (log/info "Running state monitor" !state)
  (toggle-db-connection !state state)
  (process-pending-loads !state))

(defn process-file
  [f]
  (let [rows (parse-file (str f))
        ids  (map #(get % "id") rows)]
    {:f    (str f)
     :c    (count rows)
     :rows (into #{} ids)}))

(defn find-event-in-file
  "Finds an event in the file"
  [file-name id]
  (let [rows (parse-file file-name)]
    (first (filter #(= id (get % "id")) rows))))

(defn find-extra-keys
  [backup-files]
  (let [n-fields ["content"
                  "created_at"
                  "id"
                  "kind"
                  "pubkey"
                  "sig"
                  "tags"
                  "karma"
                  "seen_on"]]
    (->> backup-files
         (map #(parse-file (str %)))
         flatten
         (map #(apply dissoc % n-fields))
         (filter seq))))

(defn count-all
  ([] (count-all nil))
  ([target-pubkey]
   (if (db-started?)
     (let [q '(-> (from :events [*])
                  (aggregate {:c (row-count)}))]
       (:c (first (xt/q node q {:args {:target-pubkey target-pubkey}}))))
     0)))

(defn clerk-command
  [& [args]]
  (log/info "args" args)
  (start-services! {::clerk-port "7777" ::nrepl-port "7000"})
  (loop []
    (Thread/sleep (* 3600 1000))
    (recur)))

(defn -main
  [& args1]
  (println "starting main")
  (prn args1)
  (prn *command-line-args*)
  (let [[args] args1]
    (println "args" args)))
