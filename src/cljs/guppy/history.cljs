(ns guppy.history
  (:require [goog.history.EventType :as EventType]
            [goog.events :as ev]
            [cljs.core.async :refer [put! chan]])
  (:import goog.history.Html5History))

(def history (Html5History.))
(.setPathPrefix history "")
(.setEnabled history true)

(def navigation (chan))

(ev/listen history EventType/NAVIGATE (fn [e] (put! navigation (.-token e))))

(defn current-token [] (.getToken history))

(defn set-token! [token] (.setToken history token))

(ev/listen js/window "load" (fn [e] (put! navigation (current-token))))
