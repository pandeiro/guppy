(ns guppy.dangerous)

;; taken entirely from https://github.com/holmsand/reagent/issues/14#issuecomment-34302638

(defn- fast-mapv
  [f coll]
  (let [res (reduce-kv
             (fn [c k v]
               (let [v' (f v)]
                 (if-not (identical? v' v)
                   (assoc! (if (nil? c) (transient coll) c)
                           k v'))))
             nil coll)]
    (if (nil? res) coll (persistent! res))))

(defn elem-map
  "Return the result of applying f to each vector in elems,
including sub-vectors. Warning: uses recursion."
  [f elems]
  (letfn [(em [e]
            (if (vector? e)
              (fast-mapv em (f e))
              e))]
    (em elems)))

(defn children
  "Return the children part of a hiccup form (or nil if there aren't any)."
  [elem]
  (let [first-child (if (-> elem (get 1) map?) 2 1)]
    (if (> (count elem) first-child)
      (subvec elem first-child))))

(defn props
  "Return the props map of a hiccup form (or nil)."
  [elem]
  (if-let [p (get elem 1)]
    (if (map? p) p)))

(defn live-dangerously [elems]
  (elem-map (fn [e]
              (if (-> e meta :danger)
                [(e 0)
                 (assoc (props e)
                   :dangerouslySetInnerHTML
                   {:__html (-> e children first)})]
                e))
            elems))

;; (defn some-comp []
;;   (live-dangerously
;;    [:div
;;     [:p "Text in " ^:danger [:b "<i>italic</i>"]]]))
