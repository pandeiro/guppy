(ns guppy.util)

(defn headline-content [e]
  (.-textContent (.-target e)))

(defn event-content [e]
  (.-value (.-target e)))

(defn random-id []
  (apply str (take 8 (shuffle "0123456789abcdef"))))

(defn document-id-from-token [token]
  (second (re-find #"/doc/(.+)" token)))

(defn doc-by-id
  "Return a map from a seq based on :id"
  [data id]
  (let [x (first (filter #(= (:id %) id) data))]
    x))

(defn relative-height [h pct]
  {:height (str (* h pct) "px")})
