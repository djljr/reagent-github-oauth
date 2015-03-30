(ns reagent-github-oauth.core
  (:require 
    [reagent.core :as reagent :refer [atom]]
    [reagent.session :as session]
    [ajax.core :refer [GET]]
    [secretary.core :as secretary :include-macros true]
    [goog.events :as events]
    [goog.history.EventType :as EventType]
    [cljsjs.react :as react])
  (:import goog.History))


(enable-console-print!)

(def user (atom {}))

;; -------------------------
;; Views

(defn home-page []
  [:div [:h2 "Welcome to reagent-github-oauth"]
   [:div [:a {:href "#/about"} "go to about page"]
         (if (@user "email")
          [:a {:href "/logout"} (@user "email")]
          [:a {:href "/login"} "Login"])]])

(defn about-page []
  [:div [:h2 "About reagent-github-oauth"]
   [:div [:a {:href "#/"} "go to the home page"]]])

(defn current-page []
  [:div [(session/get :current-page)]])


;; -------------------------
;; Server Requests

(defn update-user []
  (GET "/api/userinfo"
    {:handler (fn [data]
                (reset! user (into @user data)))
     :error-handler (fn [response]
                      (println "ERROR: " (str response)))}))

;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (session/put! :current-page #'home-page)
  (GET "/api/userinfo" {:handler (fn [data]
                                  (.setInterval js/window update-user 60000)
                                  (reset! user (into @user data)))
                        :error-handler (fn [response]
                                         (println "ERROR: " (str response)))}))

(secretary/defroute "/about" []
  (session/put! :current-page #'about-page))

;; -------------------------
;; History
;; must be called after routes have been defined
(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
     EventType/NAVIGATE
     (fn [event]
       (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

;; -------------------------
;; Initialize app
(defn mount-root []
  (reagent/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (hook-browser-navigation!)
  (mount-root))
