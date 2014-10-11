(ns guppy.geolocation)

(def geo (.-geolocation js/navigator))

(def default-options
  #js {:enableHighAccuracy true
       :maximumAge         30000
       :timeout            27000})

(defn position->clj [obj]
  (let [coords (.-coords obj)]
    {:latitude          (.-latitude coords)
     :longitude         (.-longitude coords)
     :accuracy          (.-accuracy coords)
     :altitude          (.-altitude coords)
     :altitude-accuracy (.-altitudeAccuracy coords)
     :heading           (.-heading coords)
     :speed             (.-speed coords)
     :timestamp         (.-timestamp obj)}))

(defn get-position [f & [opts]]
  (.getCurrentPosition geo #(f (position->clj %)) nil
                       (or opts default-options)))

(defn watch-position [f & [opts]]
  (.watchPosition geo #(f (position->clj %)) nil
                  (or opts default-options)))

(defn clear-watch [watch-id]
  (.clearWatch geo watch-id))
