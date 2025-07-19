^{:nextjournal.clerk/visibility {:code :hide}}
(ns backup-merge.core-notebook
  ;; {:nextjournal.clerk/toc true}
  (:require
   [babashka.fs :as fs]
   [backup-merge.core :as bm]
   [backup-merge.helpers :as helpers]
   [backup-merge.notebook-utils :as nu]
   [backup-merge.state :as state]
   [clojure.set :as set]
   [clojure.string :as str]
   [nextjournal.clerk :as clerk]
   [nextjournal.clerk.viewer :as v]
   [orgmode.core :as org]
   [xtdb.api :as xt]))

;; # Backup Merge

;; [Core](../../main/backup_merge/core.clj)

{::clerk/visibility {:code :hide :result :hide}}

;; (def s1 (:rows (bm/process-file f1)))
;; (def s2 (:rows (bm/process-file f2)))
;; (def i1 (set/intersection s1 s2))
;; (def target-id (first i1))

;; (def target-event (get-in @!state [:filters :event]))
;; (def target-pubkey (get-in @!state [:filters :pubkey]))

^{:clerk/no-cache true}
(bm/state-monitor state/!state @state/!state)

{::clerk/visibility {:code :hide :result :show}}

(clerk/with-viewer helpers/toggle-xtdb-state {:state state/!state})

;; (clerk/html (pagination-controls))

;; ^{::clerk/no-cache true}
;; (clerk/html (file-picker))

(clerk/code @state/!state)

;; (clerk/table (file-diff2 !state))

;; ^{::clerk/no-cache true}
;; (clerk/html (db-viewer target-pubkey target-event))

^{::clerk/visibility {:code :hide :result :hide}}
(comment

  (bm/event-query)

  ;; (bm/get-db-events target-event target-pubkey)

  (let [q '(-> (from :events [*]) (aggregate {:c (row-count)}))]
    (xt/q bm/node q))

  "33f1453db8737237a39b584c8eb20345cc391d54ad81cf91dc0715ad574812ed"

  (bm/get-tags)

  (xt/q bm/node
        '(-> (from :tags [*])
             (where (= tag "e"))
             (limit 5)
             (order-by position)))

  (xt/q bm/node
        '(unify
          (from :events [{:xt/id event-id #_#_:tags ts} content sig])
          (join (-> (from :events [{:xt/id event-id} tags])
                    (unnest {:tag tags})
                    (unnest {:tag2 tag})
                    (where (= tag2 "e"))
                    (unnest {:value tag})
                    (where (= value "33f1453db8737237a39b584c8eb20345cc391d54ad81cf91dc0715ad574812ed"))
                    (limit 10))
                [event-id #_tags #_content])))

  (xt/q bm/node
        '(-> (from :events [{:xt/id event-id} content *])
             (limit 10)))

  (bm/purge-db!)

  (bm/all-ids)

  ;; (bm/find-event-in-file f1 target-id)
  ;; (bm/find-event-in-file f2 target-id)

  (clerk/show! "src/notebooks/backup_merge/core_notebook.clj")

  (xt/execute-tx bm/node (bm/insert-events helpers/trimmed-events))

  #_|)
