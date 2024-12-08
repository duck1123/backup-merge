(ns notebooks.backup-merge.core-notebook
  (:require
   [babashka.fs :as fs]
   [nextjournal.clerk :as clerk]))

;; # Backup Merge

(def data-path (fs/absolutize (fs/path "data")))

(fs/cwd)

(clerk/html
 [:ul (map
       (fn [p]
         [:li (str p)])
       (fs/list-dir data-path))])
