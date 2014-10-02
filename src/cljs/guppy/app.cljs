(ns guppy.app
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [guppy.config :refer [resolve-config]])
  (:require [goog.events :as ev]
            [cljs.core.async :as async :refer [chan put! <! >!]]
            [cljs.reader :refer [read-string]]
            [reagent.core :as r :refer [render-component]]
            [weasel.repl :as repl]
            [guppy.history :as history]))

(repl/connect "ws://localhost:9001" :verbose true)


(defn document-id-from-token [token]
  (second (re-find #"/doc/(.+)" token)))




(defn random-id []
  (apply str (take 8 (shuffle "0123456789abcdef"))))

(def config (resolve-config))

(def app-state
  (r/atom
   {:name :guppy
    :view :main
    :opts {}
    :data [{:id   "9a6ecd57"
            :name "welcome"
            :text "# This is a document"
            :del  false
            :ts   1412200223710}]}))

(defn sync! [k r o n]
  (let [data {:old (:data o) :new (:data n)}]
    (.log js/console "sync!")
    (when (not= (:old data) (:new data))
      (.log js/console "sync! detected new data")
      (.setItem js/localStorage (name (:name @app-state)) (pr-str (:new data))))))

(add-watch app-state :data-store sync!)

(defn restore! []
  (when-let [data (not-empty (.getItem js/localStorage (name (:name @app-state))))]
    (swap! app-state assoc-in [:data] (read-string data))))

(defn new-document [& [{:keys [id name text]}]]
  {:id   (or id (random-id))
   :name (or name "")
   :text (or text "")
   :del  false
   :ts   (.getTime (js/Date.))})

(defn add-document []
  (swap! app-state update-in [:data] conj (new-document)))

(defn list-view [state]
  [:div
   [:button
    {:on-click #(js/alert "hi")}
    "+"]
   [:ol
    (for [doc (:data @state)]
      ^{:key (:id doc)}
      [:li [:a {:href (str "#/doc/" (:id doc))} (:name doc)]])]])

(defn doc-by-id [data id]
  (.log js/console "doc-by-id" id)
  (let [x (first (filter #(= (:id %) id) data))]
    (.log js/console (pr-str x))
    x))

(defn replace-doc [id path value doc]
  (.log js/console "replace-doc doc id: " (:id doc))
  (if (= id (:id doc))
    (do
      (.log js/console (str "got an update-doc match for id " id))
      (-> doc
        (assoc-in path value)
        (assoc-in [:ts] (.getTime (js/Date.)))))
    doc))

(defn updater [id path value]
  (fn [state]
    (merge state
      {:data 
       (if (doc-by-id (:data state) id)
         (map (partial replace-doc id path value)
           (:data state))
         (conj (:data state)
           (assoc-in (new-document {:id id})
             path value)))})))

(defn update-doc! [id path value]
  (swap! app-state (updater id path value)))

(defn document-view [state]
  (let [id   (document-id-from-token (history/current-token))
        doc  (doc-by-id (:data @state) id)]
    [:div
     [:p (pr-str doc)]
     [:h3
      {:content-editable true
       :placeholder "name"
       :on-key-up   (fn [e]
                      (update-doc!
                       id [:name]
                       (.-textContent (.-target e))))}
      (or (not-empty (:name doc)) "untitled")]
     [:textarea
      {:placeholder "Document text goes here..."
       :on-change   (fn [e]
                      (update-doc!
                       id [:text]
                       (.-value (.-target e))))}
      (:text doc)]]))

(defn init
  "A single entrypoint for the application"
  []
  (let [root   (.getElementById js/document "root")
        render (fn [view]
                 (.log js/console "rendering view" view)
                 (render-component [view app-state] root))]

    (restore!)

    (go (while true
          (let [token (<! history/navigation)]
            (condp re-find token
              #"/doc/.+" (render document-view)
              #".*"      (render list-view)
              (.log js/console "got a token but not matching")))))

    ;; (let [token (not-empty (current-token))]
    ;;   (.log js/console (str "init saw token " token))
    ;;   (if token
    ;;     (set-token! token)
    ;;     (set-token! "/")))

))

(.addEventListener js/window "DOMContentLoaded" init)

