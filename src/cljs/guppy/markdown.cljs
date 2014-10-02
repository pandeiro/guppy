(ns guppy.markdown)

(def Converter (.-converter js/Showdown))

(def showdown (Converter.))

(defn to-html [x]
  (.makeHtml showdown x))
