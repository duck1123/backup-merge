(ns backup-merge.core
  (:require
   [clojure.string :as str]
   [mount.core :as mount :refer [defstate]]
   [nextjournal.clerk :as clerk]
   [nrepl.server :as nrepl]
   [taoensso.timbre :as log]
   [xtdb.node :as xtn]))

(defstate node
  :start (xtn/start-node)
  :stop (fn [& args]
          (log/info "stopping" args)))

(defn db-started?
  []
  (.isRealized (mount/->DerefableState (str #'node))))

(defn nrepl-handler []
  (require 'cider.nrepl)
  (ns-resolve 'cider.nrepl 'cider-nrepl-handler))

(defn start-services!
  [options]
  (when-let [clerk-port (::clerk-port options)]
    (clerk/serve!
     {:host           "0.0.0.0"
      :port           (Integer/parseInt clerk-port)
      :show-filter-fn #(str/starts-with? % "src/notebooks")
      :watch-paths    ["src/main" "src/notebooks"]}))

  (when-let [nrepl-port (::nrepl-port options)]
    (log/infof "starting server on %s:%s" "0.0.0.0" nrepl-port)
    (nrepl/start-server
     :port (Integer/parseInt nrepl-port)
     :bind "0.0.0.0"
     :handler (nrepl-handler))))


(defn clerk-command
  [& [args]]
  (log/info "args" args)
  (start-services! {::clerk-port "7777" ::nrepl-port "7000"})
  (loop []
    (Thread/sleep (* 3600 1000))
    (recur)))


(defn -main
  [& args1]
  (prn args1)
  (prn *command-line-args*)
  (let [[args] args1]
    (println "args" args)))
