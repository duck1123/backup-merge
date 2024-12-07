(ns backup-merge.example
  (:require
   [applied-science.js-interop :as j]
   [nbb.core :as nbb]
   [promesa.core :as p]))

(defn read-backup
  "Read a js file and promise parsed data"
  ([] (read-backup "./nostr-backup.js"))
  ([filename]
   (.then (nbb/slurp filename) #(js/eval (str % "\ndata")))))

(defn map-events
  [data]
  (into {} (map (fn [event] [(.-id event) event]) data)))

(defn print-lines
  [o]
  (doseq [oi o]
    (println (js/JSON.stringify  (clj->js oi)))))

(defn print-backup2
  [o]
  (println "const data =")
  (println (js/JSON.stringify (clj->js o) js/undefined 4))
  (println ";"))

(defn print-backup
  [o]
  (println (js/JSON.stringify (clj->js o))))

(defn merge-files
  [af bf]
  (p/let [a  (read-backup af)
          b  (read-backup bf)
          ao (map-events a)
          bo (map-events b)
          co (merge ao bo)
          oa (vals co)
          o  (sort-by #(j/get % :created_at) < oa)]
    (print-lines o)))

(defn -main
  [& args]
  (if (= (count args) 2)
    (let [[af bf] args]
      (merge-files af bf))
    (println "Wrong number of args")))
