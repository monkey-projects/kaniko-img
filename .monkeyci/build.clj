(ns monkeyci-kaniko.build
  (:require [cheshire.core :as json]
            [clojure.string :as cs]
            [monkey.ci.build
             [api :as api]
             [core :as bc]
             [shell :as shell]]
            [monkey.ci.utils :as u]))

(def image "docker.io/monkeyci/kaniko")
(def build-version "1.21.0")
(def release-version "1.21.0")

(def docker-creds {:id "docker-creds"
                   :path "docker.json"})

(def generate-docker-creds
  "Generates file that contains docker hub credentials"
  (bc/action-job
   "generate-docker-creds"
   (fn [ctx]
     (let [creds (->> (api/build-params ctx)
                      (select-keys ["DOCKERHUB_USERNAME" "DOCKERHUB_PASSWORD"])
                      (vals)
                      (cs/join ":")
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
  (let [wd (str "/home/monkeyci/checkout/work/" (get-in ctx [:build :build-id]))]
    (bc/container-job
     "build-image"
     {:image (str image ":" build-version)
      :script ["/kaniko/executor"
               ;; TODO Replace with shell/container-work-dir when it becomes available
               "--context" (str "dir://" wd)
               "--destination" (str image ":" release-version)]
      :container/env {"DOCKER_CONFIG" (str wd (:path docker-creds))}
      :dependencies ["generate-docker-creds"]
      :restore-artifacts [docker-creds]})))

;; Jobs to run
[generate-docker-creds
 build-image]
