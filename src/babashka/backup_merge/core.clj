(ns backup-merge.core
  (:require
   [babashka.process :refer [shell]]
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(def default-clerk-port 7777)
(def default-nrepl-port 7000)
(def nbb-src "src/nbb")

(defn execute-clojure
  [f args]
  (let [base-args ["clojure"
                   "-Axtdb"
                   (str "-X " f)]
        arg-args  (map (fn [[k v]] (str "--" (name k) " " "\"" v "\"")) args)
        cli-args  (apply conj base-args arg-args)
        cmd       (str/join " " cli-args)]
    #_(binding [*out* *err*] (println cmd))
    (:exit (shell cmd))))

(defn execute-nbb
  [f args]
  (let [base-args ["npx nbb"
                   (str "-cp " nbb-src)
                   (str "-x " f)]
        arg-args  (map (fn [[k v]] (str "--" (name k) " " v)) args)
        cli-args  (apply conj base-args arg-args)
        cmd       (str/join " " cli-args)]
    #_(println cmd)
    (:exit (shell cmd))))

(defn merge-files
  [& [args]]
  (println args)
  (let [{:keys [file-a file-b]} args]
    (execute-nbb "backup-merge.example/merge-files-command" {:file-a file-a :file-b file-b})))

(defn -main
  [& [args]]
  (let [{:keys [file-a file-b]} args]
    (prn {:file-a file-a
          :file-b file-b})))

(defn convert-command
  [& [args]]
  (let [in (first (:_arguments args))]
    (execute-nbb "backup-merge.example/convert-command" {:in in})))

(defn merge-jsonl
  [& [args]]
  (doseq [in (:_arguments args)]
    (let [reader (io/reader in)
          events (json/parsed-seq reader)]
      (println (str "|" in "|" (count events) "|"))
      #_(doseq [v (take 5 events)]
          (println (get v "id"))))))

(defn start-clerk
  [{:keys [clerk-port nrepl-port]}]
  (let [f    "backup-merge.core/clerk-command"
        opts {:clerk-port clerk-port :nrepl-port nrepl-port}]
    (execute-clojure f opts)))

(defn list-daily-org-files
  [& [args]]
  (let [f "backup-merge.core/list-daily-org-files"
        opts {}]
    (execute-clojure f opts)))

(defn list-org-topic-files
  [& [args]]
  (let [f "backup-merge.core/list-org-topic-files"
        opts {}]
    (execute-clojure f opts)))

(defn list-backup-files
  [& [args]]
  (let [f "backup-merge.core/list-backup-files"
        opts {}]
    (execute-clojure f opts)))

(defn fetch-org-file
  [& [args]]
  (let [f    "backup-merge.core/fetch-org-file"
        opts {:date (str "\\\"" (:date args) "\\\"")}]
    (execute-clojure f opts)))

(defn parse-org-file
  [& [args]]
  (let [f    "backup-merge.core/parse-org-file"
        opts {:date (str "\\\"" (:date args) "\\\"")}]
    (execute-clojure f opts)))

(def build-configuration
  {:command     "build"
   :short       "b"
   :description "Not used"
   :opts        [{:option "file-a"
                  :type   :string}
                 {:option "file-b"
                  :type   :string}]
   :runs        -main})

(def clerk-configuration
  {:command     "clerk"
   :description "notebooks"
   :subcommands
   [{:command     "start"
     :description "Start the server"
     :opts
     [{:option      "clerk-port"
       :description "Port to serve clerk notebooks on"
       :type        :int
       :default     default-clerk-port}
      {:option      "nrepl-port"
       :description "Port to serve nRepl server on"
       :type        :int
       :default     default-nrepl-port}]
     :runs        start-clerk}]})

(def convert-configuration
  {:command     "convert"
   :short       "c"
   :description "Convert backup js to jsonl"
   :opts        [{:option "in"
                  :short  "0"
                  :type   :string}]
   :runs        convert-command})

(def merge-configuration
  {:command     "merge"
   :short       "m"
   :description "merge backup files"
   :subcommands
   [{:command     "js"
     :description "merge js backup"
     :opts        [{:option "file-a"
                    :type   :string}
                   {:option "file-b"
                    :type   :string}]
     :runs        merge-files}
    {:command     "jsonl"
     :description "merge jsonl backups"
     :runs        merge-jsonl}]})

(def nostr-configuration
  {:command "nostr"
   :short "n"
   :description "nostr commands"
   :subcommands
   [{:command "list-backups"
     :description "list backup files"
     :runs list-backup-files}]})

(def org-configuration
  {:command     "org"
   :description "org files"
   :short       "o"
   :subcommands [{:command "fetch"
                  :description "Fetch a daily file"
                  :opts        [{:option "date"
                                 :type   :string}]
                  :runs        fetch-org-file}
                 {:command     "list-daily"
                  :description "list daily org files"
                  :runs        list-daily-org-files}
                 {:command     "list-topics"
                  :description "list org topic files"
                  :runs        list-org-topic-files}
                 {:command     "parse"
                  :description "parse org file by date"
                  :opts        [{:option "date"
                                 :type   :string}]
                  :runs        parse-org-file}]})

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(def CONFIGURATION
  {:app
   {:command     "bm"
    :description "Backup merges"
    :version     "0.0.1"}
   :global-opts []
   :commands
   [build-configuration
    clerk-configuration
    convert-configuration
    merge-configuration
    org-configuration]})
