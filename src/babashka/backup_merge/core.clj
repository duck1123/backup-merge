(ns backup-merge.core
  (:require
   [babashka.process :refer [shell]]
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as str])
  (:import
   (java.io PushbackReader)))

(defn execute-nbb
  [f args]
  (let [cli-args (apply conj
                        ["npx nbb"
                         "-cp src"
                         (str "-x " f)]
                        (map (fn [[k v]] (str "--" (name k) " " v)) args))
        cmd      (str/join " " cli-args)]
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
    (println "main")
    (prn args)
    (prn {:file-a file-a
          :file-b file-b})))

(defn convert-command
  [& [args]]
  #_(println args)
  (let [in (first (:_arguments args))]
    (execute-nbb "backup-merge.example/convert-command" {:in in})))

(defn merge-jsonl
  [& [args]]
  #_(println args)
  (let [in (or (first (:_arguments args))
               (:in args))]
    (comment)
    (let [reader (io/reader in)

          #_(PushbackReader, in)]
      (doseq [v (json/parsed-seq reader)]
        (println #_"." (get v "id")))
      #_(println "in" in))))

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(def CONFIGURATION
  {:app
   {:command     "bm"
    :description "Backup merges"
    :version     "0.0.1"}
   :global-opts []
   :commands
   [{:command "build"
     :short   "b"
     :opts    [{:option "file-a"
                :type   :string}
               {:option "file-b"
                :type   :string}]
     :runs    -main}
    {:command     "convert"
     :short       "c"
     :description "Convert backup js to jsonl"
     :opts        [{:option "in"
                    :short  "0"
                    :type   :string}]
     :runs        convert-command}
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
       :opts        [{:option "in"
                      :short  "0"
                      :type   :string}]
       :runs        merge-jsonl}]}]})
