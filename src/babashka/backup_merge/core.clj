(ns backup-merge.core
  (:require
   [babashka.process :refer [shell]]
   [clojure.string :as str]))

(defn merge-files
  [& [args]]
  (println args)
  (let [{:keys [file-a file-b]} args
        args   ["npx nbb"
                "-cp src"
                "-x backup-merge.example/merge-files-command"
                #_"--"
                "--file-a"
                file-a
                "--file-b"
                file-b]]
    (:exit (shell (str/join " " args)))))

(defn -main
  [& [args]]
  (let [{:keys [file-a file-b]} args]
    (println "main")
    (prn args)
    (prn {:file-a file-a
          :file-b file-b})))

(defn convert-command
  [& [args]]
  (println args))

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
                :type   :string}]
     :runs    convert-command}
    {:command "merge-files"
     :opts    [{:option "file-a"
                :type   :string}
               {:option "file-b"
                :type   :string}]
     :runs    merge-files}]})
