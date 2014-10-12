(ns guppy.leaf
  (:require [reagent.core :as r]))

(def thunderforest-url
  "http://{s}.tile.thunderforest.com/landscape/{z}/{x}/{y}.png")

(def osm-attribution
  {:attribution
   "&copy; <a href=\"http://osm.org/copyright\">OpenStreetMap</a> contributors"})

(def max-zoom {:maxZoom 18})


(defn map-component []
  (let [state (r/atom {})
        id "leaflet"]
    (r/create-class
     {:render
      (fn []
        [:div {:id id :style {:width "100%" :height "300px"}}])
      :component-did-mount
      (fn [this]
        (.log js/console "mounted")
        (let [m (.map js/L id)
              layer (.tileLayer js/L thunderforest-url
                                (clj->js (merge osm-attribution max-zoom)))]
          (.setView m #js [51.505, -0.09] 13)
          (.addTo layer m)))})))

