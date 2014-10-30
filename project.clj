(defproject guppy "0.1.0-SNAPSHOT"
  :description "Something that does something."

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2371"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [ring/ring-core "1.2.2"]
                 [ring-refresh "0.1.1"]
                 [enlive "1.1.5"]
                 [com.googlecode.htmlcompressor/htmlcompressor "1.4"]
                 [com.cemerick/piggieback "0.1.3"]
                 [weasel "0.4.0-SNAPSHOT"]
                 [reagent "0.4.2"]
                 [garden "1.2.3"]
                 [clj-webjars "0.9.0"]
                 [org.webjars/momentjs "2.8.3"]
                 [org.webjars/leaflet "0.7.3"]]

  :source-paths ["src/clj"]

  :plugins [[lein-cljsbuild "1.0.4-SNAPSHOT"]
            [lein-ring "0.8.12"]
            [lein-pdo "0.1.1"]]

  :resources-paths ["app"]

  :ring {:handler guppy.dev.server/handler
         :port    3000}

  :repl-options {:init-ns guppy.dev.repl}

  :cljsbuild
  {:builds [{:id           "dev"
             :source-paths ["src/clj" "src/cljs"]
             :compiler     {:output-to     "app/guppy.js"
                            :output-dir    "app/out"
                            :optimizations :none
                            :source-map    true}}
            {:id           "production"
             :source-paths ["src/clj" "src/cljs" "app/vendor"]
             :compiler     {:output-to        "dist/app.js"
                            :optimizations    :advanced
                            :pretty-print     false
                            :externs          ["react.js"
                                               "showdown.min.js"
                                               "moment-with-locales.js"
                                               "leaflet.js"]
                            :preamble         ["react.min.js"
                                               "showdown.min.js"
                                               "moment-with-locales.min.js"
                                               "leaflet.min.js"]
                            :closure-warnings {:externs-validation :off
                                               :non-standard-jsdoc :off}}}]}

  :aliases {"dev" ["pdo"
                   ["cljsbuild" "clean"]
                   ["cljsbuild" "auto" "dev"]
                   ["ring" "server-headless"]]})
