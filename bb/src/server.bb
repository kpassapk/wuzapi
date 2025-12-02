#!/usr/bin/env bb
#_" -*- mode: clojure -*-"

(ns server
  (:require [ruuter.core :as ruuter]
            [org.httpkit.server :as server]
            [portal.api :as p]
            [cheshire.core :as json]
            [taoensso.timbre :as timbre]
            [babashka.http-client :as http]))

; From ring.middleware.json
(defn json-request? [request]
  (when-let [type (get-in request [:headers "content-type"])]
    (seq (re-find #"^application/(.+\+)?json" type))))

(defn read-json [request]
  (when (json-request? request)
    (when-let [body (:body request)]
      (let [encoding "UTF-8"
            body-reader (java.io.InputStreamReader. body encoding)]
        (json/parse-stream body-reader true)))))

(defn json-body-request [request]
  (if-let [json (read-json request)]
    (assoc request :body json)
    request))

(defn wrap-json-body [handler]
  (fn [request]
    (if-let [request (json-body-request request)]
      (handler request)
      {:error "malformed JSON request body"})))

(defn wrap-tap [handler]
  (fn [request]
    (tap> request)
    (handler request)))

(defn wrap-log [handler log]
  (fn [request]
    (timbre/info log)
    (handler request)))

(comment
  (json-request? {:headers {"content-type" "application/json"}})
  )

(defn ok [_]
  {:status 200
   :body "OK"})

;; --- API ---

(def routes [{:path "/"
              :method :get
              :response (wrap-log ok "GET /")}

             {:path "/webhooks"
              :method :post
              :response (wrap-log ok "POST /webhooks")}])


(def handler
  (-> #(ruuter/route routes %)
      wrap-tap
      wrap-json-body))

;; --- Call Out ---

(comment

  ;; Portal 
  (p/start {:port 4400})
  (add-tap #'p/submit)
  
  ; Health
  (def base-url "http://localhost:8080")

  (def user-token "abcabc")
  
  (def auth-headers
    {"Content-Type" "application/json"
     "Token" user-token})

  (defn health []
    (let [url (str base-url "/health")]
      (-> (http/get url)
          :body
          (json/parse-string true))))

  (-> (health) (doto tap>))

  (defn user-info "Gets information about users on WhatsApp" [users]
    (let [url (str base-url "/user/info")
          headers auth-headers
          body (json/generate-string {"Phone" users})]
      (-> (http/post url {:headers headers :body body})
          :body
          (json/parse-string true))))


  (def me (user-info ["14154305703@s.whatsapp.net"]))

  (tap> me)
  ;; Messages

  (defn react "Reacts to a message" [phone body id]
    (let [url (str base-url "/chat/react")
          headers auth-headers
          body (json/generate-string {"Phone" phone
                                      "Body" body
                                      "Id" (str "me:" id)})]
      (-> (http/post url {:headers headers :body body})
          :body
          (json/parse-string true))))

  ;; Doesn't work
  (react "5215573434769" "🔧" "3EB0DBC075F3F46D4CCC68")

  ;; Sends a text message

  (defn send-text "Sends a text message" [phone body id]
    (let [url (str base-url "/chat/send/text")
          headers auth-headers
          body (json/generate-string {"Phone" phone
                                      "Body" body
                                      "Id" id})]
      (-> (http/post url {:headers headers :body body})
          :body
          (json/parse-string true))))

  (str (java.util.UUID/randomUUID))
  
  (send-text "5215573434769" "nice stir-fry btw" "361db816-2057-4b72-a436-9fcc4421597d")
  
  ;; Groups
          
  )

(defn -main []
  (let [p (p/start {:port 3300})]
    (add-tap #'p/submit)
    (timbre/info "Starting server on port 9000 and Portal on port 3300")
    (server/run-server handler {:port 9000})))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
