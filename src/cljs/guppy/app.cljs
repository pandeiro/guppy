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
            [guppy.markdown :as markdown]
            [guppy.media :as media]
            [guppy.dangerous :as dangerous]
            [guppy.util :as u]))

(repl/connect "ws://localhost:9001" :verbose true)

(def config (resolve-config))

(def root (.getElementById js/document "root"))

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

(defn modal [& content]
  [:div
   {:style {:position "fixed"
            :top 0, :left 0, :right 0, :bottom 0
            :z-index 1000}}
   content])

(defn list-view [state]
  [:div
   [:button
    {:on-click (fn [e]
                 (let [id (u/random-id)]
                   (r/render-component
                    [modal
                     [:button
                      {:on-click #(history/set-token! (str "/doc/" id))}
                      "doc"]
                     [:button
                      {:on-click #(history/set-token! (str "/video"))}
                      "cam"]]
                    root)))}
    "+"]
   [:button
    {:on-click (fn [e] (history/set-token! "/video"))}
    "cam"]
   [:ol
    (for [doc (reverse (sort-by :ts (:data @state)))]
      ^{:key (:id doc)}
      [:li
       [:a
        {:href (str "#/doc/" (:id doc))}
        [:span (.fromNow (js/moment (:ts doc)))]
        " - "
        [:span [:strong (:name doc)]]]])]])



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
       (if (u/doc-by-id (:data state) id)
         (map (partial replace-doc id path value)
           (:data state))
         (conj (:data state)
           (assoc-in (new-document {:id id})
             path value)))})))

(defn update-doc! [id path value]
  (swap! app-state (updater id path value)))


(defn document-view [state]
  (let [options (r/atom {:view :edit})
        change-view (fn [k]
                      [:button
                       {:on-click (fn [e] (swap! options assoc :view k))
                        :class (if (= k (:view @options)) "active")}
                       (name k)])]
    (fn []
      (let [id   (u/document-id-from-token (history/current-token))
            doc  (u/doc-by-id (:data @state) id)]
        [:div
         [:div
          (change-view :raw)
          (change-view :edit)
          (change-view :render)]

         (case (:view @options)
           :raw    (with-meta
                     [:div
                      [:p (pr-str doc)]]
                     {:component-did-mount #(.log js/console "got a raw deal")})

           :edit   [:div
                    [:h3
                     {:content-editable true
                      :placeholder "name"
                      :on-key-up   #(update-doc! id [:name] (u/headline-content %))}
                     (or (not-empty (:name doc)) "untitled")]
                    [:textarea
                     {:placeholder "Document text goes here..."
                      :on-change   #(update-doc! id [:text] (u/event-content %))}
                     (:text doc)]]

           :render (dangerous/live-dangerously
                    ^:danger [:div (markdown/to-html (:text doc))]))]))))

(def sources (r/atom []))

(defn video-view []
  (let []
    (fn []
      [:div
       [:p "Sources"]
       [:span
        (pr-str @sources)]
       [:p "Video"]
       [:video {:auto-play true}]])))

(defn load-video [this]
  (let [video (.querySelector (r/dom-node this) "video")]
    (media/get-sources (fn [items]
                         (swap! sources conj (media/get-source :environment items))))
    (.webkitGetUserMedia js/navigator
     media/hd-constraints
     (fn [stream]
       (set! (.-src video) (media/object-url stream)))
     (fn [error]
       ))))

(defn init
  "A single entrypoint for the application"
  []
  (let [render (fn [view]
                 (render-component [view app-state] root))]

    (local/restore! app-state)

    (go (while true
          (let [token (<! history/navigation)]
            (condp re-find token
              #"/doc/.+" (render document-view)
              #"/video"  (render (with-meta video-view
                                   {:component-did-mount load-video}))
              #".*"      (render list-view)))))

    (.locale js/moment "pt-br")
    ))

(.addEventListener js/window "DOMContentLoaded" init)

