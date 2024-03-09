(ns monkeyci-kaniko.build
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.string :as cs]
            [monkey.ci.build
             [api :as api]
             [core :as bc]
             [shell :as shell]]
            [monkey.ci.utils :as u]))

(def image "docker.io/monkeyci/kaniko")
(def build-version "1.21.0")
(def release-version "1.21.0")

;; File must be called config.json by kaniko
(def docker-creds {:id "docker-creds"
                   :path "config.json"})

(def generate-docker-creds
  "Generates file that contains docker hub credentials"
  (bc/action-job
   "generate-docker-creds"
   (fn [ctx]
     (let [creds (-> (api/build-params ctx)
                     (select-keys ["DOCKERHUB_USERNAME" "DOCKERHUB_PASSWORD"])
                     (vals)
                     (as-> s (cs/join ":" s))
                     (u/->base64))
           json (json/generate-string
                 {"auths"
                  {"https://index.docker.io/v1/"
                   {"auth" creds}}})
           file (shell/in-work ctx (:path docker-creds))]
       (println "Writing docker credentials to" file)
       (spit file json)))
   {:save-artifacts [docker-creds]}))

(defn build-image [ctx]
  ;; TODO Replace with shell/container-work-dir when it becomes available
  (let [wd (str "/opt/monkeyci/checkout/work/" (get-in ctx [:build :build-id]))
        docker-config (str (fs/path wd (:path docker-creds)))]
    (bc/container-job
     "build-image"
     ;; Kaniko can't build itself because it tries to copy over its own executable
     ;; so we copy the executable to /tmp before proceeding
     {:image (str image ":" build-version)
      :script ["cp -rf /kaniko /tmp"
               (format "/tmp/kaniko/executor --context %s --destination %s"
                       (str "dir://" wd) (str image ":" release-version))]
      :container/env {"DOCKER_CONFIG" wd}
      :dependencies ["generate-docker-creds"]
      :restore-artifacts [docker-creds]})))

;; Jobs to run
[generate-docker-creds
 build-image]
