(ns backup-merge.state
  (:require
   [babashka.fs :as fs]
   [backup-merge.core :as bm]
   [nextjournal.clerk :as clerk]))

^{::clerk/sync true}
(defonce !state
  (atom
   (let [backup-files (bm/get-backup-files)]
     {:backup-file-lines 33
      :backup-page       1
      :org-path          (str (fs/path bm/base-path "daily/2024-09-15.org"))
      :event-count       1
      :event-page        1
      :pending-loads     []
      :file-a            (first backup-files)
      :file-b            (second backup-files)
      :filters           {:event  nil
                          :kind   nil
                          :pubkey nil}
      :xtdb              {:expected false
                          :actual   (bm/db-started?)}})))
