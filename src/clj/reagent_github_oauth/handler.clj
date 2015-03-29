(ns reagent-github-oauth.handler
  (:require [compojure.core :refer [GET ANY defroutes]]
            [compojure.route :refer [not-found resources]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [selmer.parser :refer [render-file]]
            [prone.middleware :refer [wrap-exceptions]]
            [environ.core :refer [env]]
            [cemerick.friend :as friend]
            [friend-oauth2.workflow :as oauth2]
            [friend-oauth2.util :refer [format-config-uri]]
            [cemerick.friend [workflows :as workflows]
                             [credentials :as creds]]
            [cheshire.core :as cheshire]
            [clj-http.client :as client]
            [ring.util.codec :as codec]
            [ring.util.response :as response])
  (:use [ring.middleware.session :only [wrap-session]]))

(def config 
  (read-string (slurp "resources/config.edn")))

(defn in-dev? []
  (= :development (:env config)))

(defn call-github [endpoint access-token]
  (-> (format "https://api.github.com%s%s&access_token=%s" 
        endpoint
        (when-not (.contains endpoint "?") "?")
        access-token)
    client/get
    :body
    (cheshire/parse-string (fn [^String s] (keyword (.replace s \_ \-))))))

(defn github-credential-fn
  "Looks for the user email using the GitHub API after login with GitHub"
  [token]
  (let [access-token (:access-token token)
        user-data    (call-github "/user" access-token)
        email        (:email user-data)]
    {:identity token :email email :roles #{::user}}))

(def github-client-config (:github-oauth config))

(def github-uri-config
  {:authentication-uri {:url "https://github.com/login/oauth/authorize"
                        :query {:client_id (:client-id github-client-config)
                                :response_type "code"
                                :redirect_uri (format-config-uri github-client-config)
                                :scope ""}}

   :access-token-uri {:url "https://github.com/login/oauth/access_token"
                      :query {:client_id (:client-id github-client-config)
                              :client_secret (:client-secret github-client-config)
                              :grant_type "authorization_code"
                              :redirect_uri (format-config-uri github-client-config)
                              :code ""}}})

(defn session-email
  "Find the email stored in the session"
  [request]
  (let [token (get-in request [:session :cemerick.friend/identity :current :access-token])]
    (get-in request [:session :cemerick.friend/identity :authentications {:access-token token} :email])))

(defroutes routes
  (GET "/" [] (render-file "templates/index.html" {:dev (in-dev?)}))
  (GET "/login/github" request
    (friend/authorize #{::user} (response/redirect "/")))
  (friend/logout (ANY "/logout" request (ring.util.response/redirect "/")))
  (resources "/")
  (not-found "Not Found"))

(def friend-configuration
  {:allow-anon? true
   :workflows [
     (oauth2/workflow
       {:client-config github-client-config
        :uri-config    github-uri-config
        :credential-fn github-credential-fn
        :access-token-parsefn #(-> % :body codec/form-decode (get "access_token"))})]})
(def app
  (let [handler (-> routes
                    (friend/authenticate friend-configuration)
                    wrap-session
                    (wrap-defaults site-defaults))]
    (if (in-dev?) (wrap-exceptions handler) handler)))
