(ns backup-merge.helpers
  ;; {:nextjournal.clerk/toc true}
  (:require
   [babashka.fs :as fs]
   [backup-merge.core :as bm]
   [backup-merge.notebook-utils :as nu]
   [backup-merge.state :as state]
   [clojure.set :as set]
   [clojure.string :as str]
   [nextjournal.clerk :as clerk]
   [nextjournal.clerk.viewer :as v]
   [orgmode.core :as org]
   [xtdb.api :as xt]))

(def backup-files (bm/get-backup-files))

(def f1 (:file-a @state/!state))
(def f2 (:file-b @state/!state))

(def trimmed-events (bm/get-trimmed-events state/!state f1))
(def trimmed-files (bm/get-trimmed-files state/!state backup-files))

(def set-file-a-button-viewer
  {:render-fn
   '(fn [p] [:button.bg-sky-500.hover:bg-sky-700.text-white.rounded-xl.px-2.py-1
             {:on-click #(swap! !state assoc-in [:file-a] p)} "Set A"])})

(def set-file-b-button-viewer
  {:render-fn
   '(fn [p] [:button.bg-sky-500.hover:bg-sky-700.text-white.rounded-xl.px-2.py-1
             {:on-click #(swap! !state assoc-in [:file-b] p)} "Set B"])})

(def load-file-button-viewer
  {:render-fn
   '(fn [p] [:button.bg-sky-500.hover:bg-sky-700.text-white.rounded-xl.px-2.py-1
             {:on-click #(swap! !state update-in [:pending-loads] conj p)} "Load"])})

(def load-org-button-viewer
  {:render-fn
   '(fn [file]
      [:button.bg-sky-500.hover:bg-sky-700.text-white.rounded-xl.px-2.py-1
       {:on-click #(swap! !state assoc-in [:org-path] file)} "Load"])})

(def filter-event-viewer
  {:render-fn
   '(fn [value]
      [:button.bg-sky-500.hover:bg-sky-700.text-white.rounded-xl.px-2.py-1
       {:on-click #(swap! !state assoc-in [:filters :event] value)}
       value])})

(def filter-pubkey-viewer
  {:render-fn
   '(fn [value]
      [:button.bg-sky-500.hover:bg-sky-700.text-white.rounded-xl.px-2.py-1
       {:on-click #(swap! !state assoc-in [:filters :pubkey] value)}
       value])})

(def reset-event-filter-viewer
  {:render-fn
   '(fn [_value]
      [:button.bg-sky-500.hover:bg-sky-700.text-white.rounded-xl.px-2.py-1
       {:on-click #(swap! !state assoc-in [:filters :event] nil)}
       "Reset Event"])})

(def reset-pubkey-filter-viewer
  {:render-fn
   '(fn [p]
      [:button.bg-sky-500.hover:bg-sky-700.text-white.rounded-xl.px-2.py-1
       {:on-click #(swap! !state assoc-in [:filters :pubkey] nil)}
       "Reset Pubkey"])})

(def increase-file-counter-viewer
  {:render-fn
   '(fn [path-obj]
      (let [path (into [] (map :nextjournal/value path-obj))]
        [:button.bg-sky-500.hover:bg-sky-700.text-white.rounded-xl.px-2.py-1
         {:on-click #(swap! !state update-in path inc)}
         "↑"]))})

(def decrease-file-counter-viewer
  {:render-fn
   '(fn [path-obj]
      (let [path (into [] (map :nextjournal/value path-obj))]
        [:button.bg-sky-500.hover:bg-sky-700.text-white.rounded-xl.px-2.py-1
         {:on-click #(swap! !state update-in path dec)}
         "↓"]))})

(def toggle-xtdb-state
  {:render-fn
   '(let [path [:xtdb :expected]]
      (fn []
        [:button.bg-sky-500.hover:bg-sky-700.text-white.rounded-xl.px-2.py-1
         {:on-click #(swap! !state update-in path not)}
         (if (get-in @!state path) "stop" "start")]))})

(defn number-spinner
  "Displays a spinner for adjusting a value at path"
  [path]
  [:ul
   [:li (clerk/with-viewer increase-file-counter-viewer path)]
   [:li (clerk/with-viewer decrease-file-counter-viewer path)]])

(defn format-e
  [event]
  (let [{:keys [content created-at
                kind pubkey sig tags]} event
        used-keys                      #{:id :pubkey :kind :content :tags :created-at :sig :xt/id}
        others                         (apply dissoc event used-keys)]
    [:li
     [:p "Author: "
      (clerk/with-viewer filter-pubkey-viewer pubkey)]
     [:p kind]
     [:p content]
     [:p (str (java.sql.Timestamp. (* created-at 1000)))]
     [:p sig]
     (when (seq others)
       [:p [:code [:pre (str others)]]])
     [:table
      [:thead
       [:tr
        [:th "tag"]
        [:th "value"]
        [:th "relay"]
        [:th "extra"]]]
      [:tbody
       (for [[tag value relays & extras] tags]
         [:tr
          [:td tag]
          [:td
           (case tag
             "p" (clerk/with-viewer filter-pubkey-viewer value)
             "e" (clerk/with-viewer filter-event-viewer value)
             value)]
          [:td relays]
          [:td (str/join ", " extras)]])]]]))

(defn format-e1
  [e]
  [:div (clerk/with-viewers clerk/default-viewers e)]
  #_[:pre [:code (str e)]])

(defn db-viewer
  [target-pubkey target-event]
  (let [started?    (bm/db-started?)
        event-count (bm/count-all target-pubkey)
        db-events   (bm/get-db-events target-event target-pubkey)]
    [:div
     [:hr]
     [:h3 "Events in database: " event-count]
     [:table
      [:tr
       [:td target-pubkey]
       [:td (clerk/with-viewer reset-pubkey-filter-viewer nil)]]
      [:tr
       [:td target-event]
       [:td (clerk/with-viewer reset-event-filter-viewer nil)]]]
     (if started?
       [:ul (map format-e db-events)]
       [:p "Database not started"])]))

(defn file-viewer
  []
  (let [event-count (:event-count @state/!state)]
    [:div
     [:p "Showing " event-count  " events"]
     [:input {:type "text" :name "foo"}]
     [:div (map #(v/with-viewer nu/nostr-event-viewer %) trimmed-events)]]))

(defn file-diff
  [s1 s2 i1]
  [["In Both"   (count i1)]
   ["In File A" (count (set/difference s1 i1))]
   ["In File B" (count (set/difference s2 i1))]])

(defn file-diff2
  [state]
  (let [f1 (:file-a @state)
        f2 (:file-b @state)
        s1 (:rows (bm/process-file f1))
        s2 (:rows (bm/process-file f2))
        i1 (set/intersection s1 s2)]
    (file-diff s1 s2 i1)))

(defn file-picker
  []
  (->> (for [p trimmed-files]
         [:tr {}
          [:td (str (fs/relativize bm/data-path p))]
          [:td (clerk/with-viewer set-file-a-button-viewer p)]
          [:td (clerk/with-viewer set-file-b-button-viewer p)]
          (when (bm/db-started?)
            [:td (clerk/with-viewer load-file-button-viewer p)])])
       (apply vector :table)))

(defn pagination-controls
  []
  [:div.border-red.border-1
   [:div {}
    [:div {} (str "Backup Page " (:backup-page @state/!state) " / "
                  (int (Math/ceil (/ (count backup-files) (:backup-file-lines @state/!state)))))]
    [:div {} (number-spinner [:backup-page])]]
   [:div {}
    [:div {} (str "Backup File Lines " (:backup-file-lines @state/!state) " / " (count backup-files))]
    [:div {} (number-spinner [:backup-file-lines])]]])

(declare process-content)

(def at true)

(defn process-content-item
  [content-item]
  (if (string? content-item)
    (str #_"str: " content-item)
    (let [{:keys [content listlevel listtype text type uri]} content-item]
      (when-not (and (= type :p) (= content []))
        (if (= type :list)
          [:ul (map (fn [c] [:li (process-content-item c)]) content)]
          (if (= type :link)
            [:div
             [:a {:href (str "#" uri)}
              [:div #_"link: " (map process-content-item content)]]
             #_[:pre#link-content [:code (pr-str content-item)]]]
            (->> [(when-not (and at (#{:p #_:link :listitem} type))
                    [:pre#ca-pre [:code (pr-str content-item)]])
                  (when (#{:list} type)
                    [:p "list Level: " listlevel])
                  (when listtype
                    [:p "list Type: " (str listtype)])
                  (when-not (#{:link :listitem :p} type)
                    [:p (str "type: " type)])
                  (when (#{:link} type)
                    [:a {:href (str "#" uri)}
                     (process-content content)])
                  (when-not (#{:link} type)
                    (when (seq text)
                      (str "text: " text)))
                  (when-not (#{:link} type)
                    (when (seq content)
                      [:div #_"-" (process-content content)]))]
                 (filter identity)
                 (into [:div {:style {:border "1px solid red"
                                      :margin-bottom "5px"}}]))))))))

(defn process-content
  [items]
  (if (every? string? items)
    (apply str (:content items))
    (->> items
         (map process-content-item)
         (into [:div #_{:style {:border "1px solid green"}}]))))

(defn process-entry
  [c]
  (let [{:keys [content tags text todo type]} c
        created                               (get-in c [:properties :created])]
    (->> [(when text [:p text])
          (when todo [:p#todo todo])
          (when (seq tags)
            (->> tags
                 (map (fn [tag] [:li tag]))
                 (into [:ul])))
          (when-not (#{:headline} type)
            [:p "Type: " (str type)])
          (when created [:p "Created: " created])
          [:pre#c-pre
           [:code
            (pr-str
             (-> c
                 identity
                 #_(dissoc :text :type :todo :tags :content)
                 #_(update :properties #(dissoc % :created))))]]
          [:div#entry-content
           #_[:pre [:code (pr-str content)]]
           (process-content content)]]
         (filter identity)
         (into [:div#entry-item
                {:style
                 {:border        "1px blue solid"
                  :margin-bottom "10px"}}]))))

(defn process-page
  [path]
  (let [page              (org/parse path)
        page-title        (get-in page [:attribs :title])
        page-id           (get-in page [:properties :id])
        top-level-content (->> (:content page)
                               identity
                               #_(take 7))]
    [:div#page {:style {:border  "1px solid white"
                        :padding "2px"}}
     [:h1#page-title page-title]
     [:h2#page-id page-id]
     (->> top-level-content
          (map process-entry)
          (into [:div#entry-list]))]))

(defn org-directory-viewer
  []
  (let [page 2]
    (->> (bm/get-org-files)
         #_(filter #(fs/ends-with? % "org"))
         (filter #(= (fs/extension %) "org"))
         sort
         (drop (* (dec page) 10))
         (take (* 1 #_(dec page) 10))
         (map
          (fn [f]
            (let [base (fs/canonicalize (fs/path bm/base-path))
                  rel  (str (fs/relativize base f))
                  full (str (fs/canonicalize (fs/path bm/base-path rel)))]
              [:tr
               #_[:td (str f)]
               #_[:td (str (fs/extension f)  #_(fs/ends-with? f "org"))]
               [:td rel]
               #_[:td full]
               [:td (clerk/with-viewer load-org-button-viewer #_rel full)]])))
         (into [:table]))))

(defn org-daily-directory-viewer
  []
  (let [page 2]
    (->> (bm/get-org-daily-files)
         (filter #(= (fs/extension %) "org"))
         sort
         (drop (* (dec page) 10))
         (take 10)
         (map
          (fn [f]
            (let [base (fs/canonicalize (fs/path bm/base-path "daily"))
                  rel  (str (fs/relativize base f))
                  full (str (fs/canonicalize (fs/path base rel)))]
              [:tr
               [:td rel]
               [:td (clerk/with-viewer load-org-button-viewer #_rel full)]])))
         (into [:table]))))
