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

(defn wrap-content-type [handler]
  (fn [req]
    (println "wrap-content-type" (:uri req))
    (if (= "/" (:uri req))
      (let [utf-8-req (resp-util/content-type req "text/html;charset=UTF-8")]
        (println utf-8-req)
        (handler utf-8-req))
      (handler req))))

(def handler
  (-> base
    file-util/wrap-file-info
    wrap-content-type
    (webjars/wrap-webjars ["vendor"])
    (refresh/wrap-refresh ["app"])))

