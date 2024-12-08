(ns notebooks.backup-merge.core-notebook
  (:require
   [babashka.fs :as fs]
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [nextjournal.clerk :as clerk]))

;; # Backup Merge

(def data-path (fs/absolutize (fs/path "data")))

(fs/cwd)

(def backup-files (fs/list-dir data-path))

(clerk/html
 [:ul (map (fn [p] [:li (str p)]) backup-files)])
