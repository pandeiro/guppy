(ns guppy.local
  (:require [cljs.reader :refer [read-string]]))

(defn sync! [k r o n]
  (let [data {:old (:data o) :new (:data n)}
        storage-key (name (:name n))]
    (when (not= (:old data) (:new data))
      (.setItem js/localStorage storage-key (pr-str (:new data))))))

(defn restore! [r]
  (when-let [data (not-empty (.getItem js/localStorage (name (:name @app-state))))]
    (swap! r assoc-in [:data] (read-string data))))