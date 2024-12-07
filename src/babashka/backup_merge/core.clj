(ns backup-merge.core)

(defn -main
  [& [args]]
  (let [{:keys [file-a file-b]} args]
    (println "main")
    (prn args)
    (prn {:file-a file-a
          :file-b file-b})))
