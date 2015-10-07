(ns sample-app.core
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [hiccup.core :as h]
            [avenue.core :as av :refer [page xhr form-post]]
            [compojure.handler :as handler]
            [ring.util.response :as ring-response]
            [ring.middleware.session.cookie :as cookie]))

(defn show-hiccup-page [req action-result]
  (h/html action-result))

(defn show-kittens-and-ponies [_req]
  [:div
    [:h1 "Kittens and ponies!"]
    [:iframe {:width "560" :height "315" :src "https://www.youtube.com/embed/ipMa_yRqQew" :frameborder "0" :allowfullscreen "allowfullscreen"}]])

(defn show-welcome-page [_request]
  [:div
    [:h1 "Welcome"]
    [:a {:href "http://zombo.com"} "You probably meant to go here"]])

(defn show-secret-page [_req] [:h1 "There's secret stuff here!"])
(defn update-user-permissions [_req]
  (prn "this does nothing, but you should only be able to see this prn statement when logged in"))

(defn login [req]
  [:form {:method "POST" :action "/login"}
   [:input {:type "submit" :value "Log In!"}]])

(defroutes app-routes
  (page
    "/welcome"
    :allowEveryone
    show-welcome-page)

  (page
    "/public/kittens-and-ponies"
    :allowEveryone
    show-kittens-and-ponies)

  (page
    "/admin/do-secret-stuff"
    :adminOnly
    show-secret-page)

  (page
    "/login"
    :allowEveryone
    login)

  (POST
    "/login"
    req
    (fn [req]
      (->
        (ring-response/response "Logged in!")
        (assoc-in [:session] {:user-id 123}))))

  (GET
    "/logout"
    req
    (fn [req]
      (->
        (ring-response/response "Logged out!")
        (assoc-in [:session] nil))))

  (xhr
    POST
    "/admin/update-user-permissions"
    :adminOnly
    update-user-permissions))

(def app (-> app-routes
             (av/wrap-auth {:auth-fn (fn [auth-data req]
                                       (condp = auth-data
                                         :allowEveryone true
                                         :adminOnly (not (nil? (:user-id (:session req))))
                                         false))
                            :authorized-page-fn show-hiccup-page
                            :unauthorized-page-fn (fn [req] (ring-response/redirect "/welcome"))})
             (handler/site {:session      {:store (cookie/cookie-store {:key "not very secret!"})}
                            :cookie-attrs {:max-age (* 3600 6)}})))
