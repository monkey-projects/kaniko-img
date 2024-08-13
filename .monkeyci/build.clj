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
(def release-version "1.23.2")
(def release-image (str image ":" release-version))

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

(defn- build-job-id [arch]
  (str "build-image-" (name arch)))

(defn build-image
  "Creates a job that builds the kaniko image for specified architecture."
  [arch]
  (fn [ctx]
    (let [wd (shell/container-work-dir ctx)]
      (bc/container-job
       (build-job-id arch)
       ;; Kaniko can't build itself because it tries to copy over its own executable
       ;; so we copy the executable to /tmp before proceeding
       {:image (str image ":" build-version)
        :script ["cp -rf /kaniko /tmp"
                 (format "/tmp/kaniko/executor --context %s --destination %s"
                         (str "dir://" wd)
                         (str release-image "-" (name arch)))]
        :arch arch
        :container/env {"DOCKER_CONFIG" wd}
        :dependencies ["generate-docker-creds"]
        :restore-artifacts [docker-creds]}))))

(def archs [:arm :amd])

(def build-jobs (mapv build-image archs))

(defn publish-manifest
  "Uses manifest-tool to merge the images built for several architectures into one
   manifest and pushes it."
  [ctx]
  (bc/container-job
   "publish-manifest"
   ;; TODO Switch to mplatform/manifest-tool as soon as MonkeyCI allows shell-less containers
   {:image "docker.io/monkeyci/manifest-tool:2.1.7"
    :script [(format "/manifest-tool --docker-cfg=%s push from-args --platforms=%s --template %s --target %s"
                     (str (shell/container-work-dir ctx) "/" (:path docker-creds))
                     (->> archs
                          (map (comp (partial str "linux/") name))
                          (cs/join ","))
                     (str release-image "-ARCH")
                     release-image)]
    :restore-artifacts [docker-creds]
    :dependencies (mapv build-job-id archs)}))

;; Jobs to run
[generate-docker-creds
 build-jobs
 publish-manifest]
