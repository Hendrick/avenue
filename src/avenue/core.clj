(ns avenue.core
  (:require
    [ring.util.response :as response]
    [compojure.core :refer [GET POST]]))

(defmacro check-auth [req success-fn fail-fn args]
  `(if-let [auth-fn# (::auth-fn ~req)]
     (if (auth-fn# ~(first args) ~req)
       (~success-fn ~req (-> ~req ~@(rest args)))
       (~fail-fn ~req))
     (~success-fn ~req (-> ~req ~@args))))

(defmacro page [path & args]
  `(GET ~path
        req#
        (check-auth
          req#
          (::authorized-page-fn req#)
          (::unauthorized-page-fn req#)
          ~args)))

(defmacro xhr [method path & args]
  `(~method ~path
            req#
            (check-auth
              req#
              ~(fn [_req action-result]
                 {:body (pr-str (if (seq? action-result)
                                  (doall action-result)
                                  action-result))
                  :headers {"Content-Type" "text/edn; charset=utf-8"}})
              ~(fn [_req]
                 {:status 403
                  :body "Forbidden"})
              ~args)))

(defmacro form-post [path & args]
  `(POST ~path req#
         (check-auth
           req#
           (fn [_req# action-result#] (response/redirect-after-post action-result#))
           (fn [_req#]
             {:status 403
              :body "Forbidden"})
           ~args)))

(defn wrap-auth [routes config]
  (fn [request]
    (routes (assoc request
                   ::auth-fn (:auth-fn config)
                   ::authorized-page-fn (:authorized-page-fn config)
                   ::unauthorized-page-fn (:unauthorized-page-fn config)))))
