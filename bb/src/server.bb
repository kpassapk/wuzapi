#!/usr/bin/env bb
#_" -*- mode: clojure -*-"

(comment
  (require '[babashka.deps :as deps])
  (deps/add-deps '{:deps {org.clojars.askonomm/ruuter {:mvn/version "1.3.5"}
                          djblue/portal {:mvn/version "0.62.0"}}})
  
  )

;; 

;; (require '[babashka.classpath :refer [add-classpath]])

;; TODO download jar files manually
;; (add-classpath "/root/.m2/repository/org/clojars/askonomm/ruuter/1.3.5/ruuter-1.3.5.jar")
;; (add-classpath "/root/.m2/repository/djblue/portal/0.62.0/portal-0.62.0.jar")

(ns server
  (:require [ruuter.core :as ruuter]
            [org.httpkit.server :as http]
            [portal.api :as p]
            [cheshire.core :as json]
            [taoensso.timbre :as timbre]))

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

(def routes [{:path "/"
              :method :get
              :response (wrap-log ok "GET /")}

             {:path "/webhooks"
              :method :post
              :response (wrap-log ok "POST /webhooks")}])

(defonce p (p/start {:port 3300}))

(def handler
  (-> #(ruuter/route routes %)
      wrap-tap
      wrap-json-body))

(defn -main []
  (add-tap #'p/submit)
  (timbre/info "Starting server on port 9000 and Portal on port 3300")
  (http/run-server handler {:port 9000}))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
