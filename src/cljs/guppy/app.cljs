(ns guppy.app
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [guppy.config :refer [resolve-config]])
  (:require [goog.events :as ev]
            [cljs.core.async :as async :refer [chan put! <! >!]]
            [cljs.reader :refer [read-string]]
            [reagent.core :as r :refer [render-component]]
            [weasel.repl :as repl]
            [guppy.history :as history]
            [guppy.geolocation :as geo]
            [guppy.leaf :as leaf]
            [guppy.local :as local]
            [guppy.markdown :as markdown]
            [guppy.util :as u]))

(repl/connect "ws://localhost:9001" :verbose true)

(def root (.getElementById js/document "root"))

(def app-state
  (r/atom
   {:name        :guppy
    :config      (resolve-config)
    :view        :main
    :viewport    {:width nil, :height nil}
    :geo-logging nil
    :opts        {:lang "en" ; en | pt-br
                  :sort :ts
                  :reverse? true
                  :remove {:del true}}
    :data        []}))

(add-watch app-state :data-store local/sync!)

(defn new-document [& [{:keys [id from name created ts text geo]}]]
  (let [now (.getTime (js/Date.))]
    {:id      (or id (u/random-id))
     :from    from
     :del     false
     :name    (or name "")
     :created (or created now)
     :ts      (or ts now)

     :text    (or text "")

     :geo     (or geo [])}))

(defn add-document [& [data]]
  (swap! app-state update-in [:data] conj (new-document data)))

(defn list-view [state]
  [:div
   [:button
    {:on-click #(history/set-token! (str "/doc/" (u/random-id)))}
    "+"]
   [:ol
    (let [removers (map (fn [[k v]] #(= (k %) v)) (get-in @state [:opts :remove]))
          remover  (partial remove (apply some-fn removers))
          sorter   (partial sort-by (get-in @state [:opts :sort]))
          reverser (if (get-in @state [:opts :reverse?]) reverse identity)

          docs     (-> (:data @state)
                     remover
                     sorter
                     reverser)]

      (for [doc docs]
        ^{:key (:id doc)}
        [:li
         {:style {:line-height "1.75em"}}
         [:a
          {:href (str "#/doc/" (:id doc))
           :style {:text-decoration "none"}}
          [:span [:strong (or (not-empty (:name doc)) "untitled")]]
          [:span
           {:style {:padding-left "6px"
                    :text-decoration "none"}}
           (.format (js/moment (:created doc)) "L")]]]))]])

(defn replace-doc [id path value doc]
  (if (= id (:id doc))
    (-> doc
      (assoc-in path value)
      (assoc-in [:ts] (.getTime (js/Date.))))
    doc))

(defn getter
  [id path]
  (fn [{data :data}]
    (get-in (first (filter #(= id (:id %)) data)) path)))

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
        change-view
        (fn [k]
          [:button
           {:on-click (fn [e] (swap! options assoc :view k))
            :class (if (= k (:view @options)) "active")}
           (name k)])
        toggle-geo
        (fn [id]
          (fn [e]
            (if (get-in @state [:geo-logging id])
              (do
                (geo/clear-watch (get (:geo-logging @state) id))
                (swap! state update-in [:geo-logging] dissoc id))
              (do
                (swap! state update-in [:geo-logging] assoc id
                  (geo/watch-position
                   (fn [pos]
                     (let [prev-vals (getter id [:geo])]
                       (update-doc! id [:geo]
                         (conj (prev-vals @state) pos))))))))))]
    (fn []
      (let [id   (u/document-id-from-token (history/current-token))
            doc  (u/doc-by-id (:data @state) id)
            h    (get-in @state [:viewport :height])]
        [:div
         [:div
          {:style (merge {:width "100%"
                          :position "fixed"
                          :left 0
                          :top 0}
                         (u/relative-height h 0.1))}
          [:button
           {:on-click (fn [e] (update-doc! id [:del] true))}
           "x"]
          (change-view :raw)
          (change-view :edit)
          (change-view :render)
          (change-view :map)
          [:button
           {:on-click (toggle-geo id)
            :class (if (get-in @state [:geo-logging id]) "on")}
           "geo"]]

         (case (:view @options)
           :raw
           [:div
            [:p (pr-str doc)]]

           :edit
           [:div
            [:div
             {:style (merge {:margin-top (:height (u/relative-height h 0.1))}
                            (u/relative-height h 0.2))}
             [:p "Last edit: " (.fromNow (js/moment (:ts doc)))]
             [:input
              {:placeholder "name"
               :on-change #(update-doc! id [:name] (u/event-content %))
               :default-value (:name doc)}]]
            [:textarea
             {:placeholder "Document text goes here..."
              :style (merge {:position "fixed"
                             :bottom 0
                             :left 0
                             :width "100%"
                             :border "none"}
                            (u/relative-height h 0.7))
              :on-change   #(update-doc! id [:text] (u/event-content %))
              :default-value (:text doc)}]]

           :map
           [leaf/map-component :leaf]
           :render
           (let [html (markdown/to-html (:text doc))]
             [:div
              [:section
               {:dangerouslySetInnerHTML {:__html html}}]]))]))))

(defn init
  "A single entrypoint for the application"
  []
  (let [render (fn [view]
                 (render-component [view app-state] root))

        list-view (with-meta
                    list-view
                    (let [kill (chan)]
                      {:component-did-mount
                       (fn [this]
                         (go-loop [kill? false]
                           (let [[v c] (async/alts!
                                        [(async/timeout 5000) kill])]
                             (when (.isMounted this)
                               (.forceUpdate this))
                             (recur (if (= c kill) true)))))
                       :component-will-unmount
                       (fn [this]
                         (put! kill :kill))}))]

    (local/restore! app-state)

    (go (while true
          (let [token (<! history/navigation)]
            (condp re-find token
              #"/doc/.+" (render document-view)
              #".*"      (render list-view)))))

    (.locale js/moment (:lang (:opts @app-state)))
    ))

(.addEventListener js/window "DOMContentLoaded" init)

(defn viewport-size-updater [r path]
  (fn [_]
    (swap! r assoc-in (conj path :width) (.-innerWidth js/window))
    (swap! r assoc-in (conj path :height) (.-innerHeight js/window))))

(def viewport-size-handler (viewport-size-updater app-state [:viewport]))

(ev/listen js/window "load" viewport-size-handler)
(ev/listen js/window "resize" viewport-size-handler)
