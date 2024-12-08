(ns backup-merge.core
  (:require
   [babashka.process :refer [shell]]
   [clojure.string :as str]))

(defn execute-nbb
  [f args]
  (let [cli-args (apply concat
                        ["npx nbb"
                         "-cp src"
                         (str "-x " f)]
                        (map (fn [[k v]] (str (name k) " " v)) args))]
    (:exit (shell (str/join " " cli-args)))))

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
  (println args)
  (let [in (first (:_arguments args))]
    (execute-nbb "backup-merge.example/convert-command" {:in in})))

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
    {:command "convert"
     :opts    [{:option "in"
                :short "0"
                :type   :string}]
     :runs    convert-command}
    {:command "merge-files"
     :opts    [{:option "file-a"
                :type   :string}
               {:option "file-b"
                :type   :string}]
     :runs    merge-files}]})
