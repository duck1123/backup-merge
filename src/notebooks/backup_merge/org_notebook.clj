(ns backup-merge.org-notebook
  (:require
   [backup-merge.helpers :as helpers]
   [backup-merge.state :as state]
   [nextjournal.clerk :as clerk]))

^{::clerk/viewer clerk/html ::clerk/no-cache true}
(helpers/org-directory-viewer)

^{::clerk/viewer clerk/html ::clerk/no-cache true}
(helpers/org-daily-directory-viewer)

^{::clerk/viewer clerk/html ::clerk/no-cache true}
(helpers/process-page (:org-path @state/!state))
