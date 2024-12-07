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
                "-m backup-merge.example"
                file-a file-b]]
    (:exit (shell (str/join " " args)))))

(defn -main
  [& [args]]
  (let [{:keys [file-a file-b]} args]
    (println "main")
    (prn args)
    (prn {:file-a file-a
          :file-b file-b})))
