(ns guppy.app
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [guppy.config :refer [resolve-config]])
  (:require [goog.events :as ev]
            [cljs.core.async :as async :refer [chan put! <! >!]]
            [cljs.reader :refer [read-string]]
            [reagent.core :as r :refer [render-component]]
            [weasel.repl :as repl]
            [guppy.history :as history]
            [guppy.local :as local]
            [guppy.util :as u]))

(repl/connect "ws://localhost:9001" :verbose true)

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

(add-watch app-state :data-store local/sync!)

(defn new-document [& [{:keys [id name text]}]]
  {:id   (or id (u/random-id))
   :name (or name "")
   :text (or text "")
   :del  false
   :ts   (.getTime (js/Date.))})

(defn add-document []
  (swap! app-state update-in [:data] conj (new-document)))

(defn list-view [state]
  [:div
   [:button
    {:on-click (fn [e]
                 (let [id (u/random-id)]
                   (history/set-token! (str "/doc/" id))))}
    "+"]
   [:ol
    (for [doc (:data @state)]
      ^{:key (:id doc)}
      [:li [:a {:href (str "#/doc/" (:id doc))} (:name doc)]])]])



(defn replace-doc [id path value doc]
  (if (= id (:id doc))
    (-> doc
      (assoc-in path value)
      (assoc-in [:ts] (.getTime (js/Date.))))
    doc))

(defn updater
  "Updates a map 'in place' within a sequence according to the
  id and path, sticking value there. Creates the map if one
  with that particular id doesn't already exist."
  [id path value]
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
  (let [id   (u/document-id-from-token (history/current-token))
        doc  (u/doc-by-id (:data @state) id)]
    [:div
     [:p (pr-str doc)]
     [:h3
      {:content-editable true
       :placeholder "name"
       :on-key-up   #(update-doc! id [:name] (headline-content %))}
      (or (not-empty (:name doc)) "untitled")]
     [:textarea
      {:placeholder "Document text goes here..."
       :on-change   #(update-doc! id [:text] (event-content %))}
      (:text doc)]]))

(defn init
  "A single entrypoint for the application"
  []
  (let [root   (.getElementById js/document "root")
        render (fn [view]
                 (render-component [view app-state] root))]

    (local/restore! app-state)

    (go (while true
          (let [token (<! history/navigation)]
            (condp re-find token
              #"/doc/.+" (render document-view)
              #".*"      (render list-view)))))))

(.addEventListener js/window "DOMContentLoaded" init)

