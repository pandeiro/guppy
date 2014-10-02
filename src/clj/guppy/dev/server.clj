(ns guppy.dev.server
  (:require
   [ring.util.response        :as resp-util]
   [ring.middleware.file-info :as file-util]
   [ring.middleware.refresh   :as refresh]
   [clj-webjars :as webjars]))

(defn base [req]
  (let [resp (or (resp-util/file-response (:uri req) {:root "app"})
                 (resp-util/not-found "Not found!"))]
    resp))

(webjars/refresh-assets!)

(def handler
  (-> base
    file-util/wrap-file-info
    (webjars/wrap-webjars ["vendor"])
    (refresh/wrap-refresh ["app"])))

