(ns guppy.media)

(defn object-url [stream]
  (.createObjectURL js/URL stream))

(def user-media
  (or (.-getUserMedia js/navigator)
      (.-webkitGetUserMedia js/navigator)
      (.-mozGetUserMedia js/navigator)
      (.-msGetUserMedia js/navigator)))

(def hd-constraints
  (clj->js {:video
            {:mandatory
             {:minWidth 1280
              :minHeight 720}}})) ;

(defn- arr [o]
  (.call (.-slice (.-prototype js/Array)) o 0))

(defn get-sources [f]
  (.getSources js/MediaStreamTrack f))

(defn get-source [k sources]
  (let [compare-facing #(= (name k) (aget % "facing"))]
    (first (filter compare-facing (.call (.-slice (array)) sources 0)))))
