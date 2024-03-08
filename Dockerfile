FROM gcr.io/kaniko-project/executor:v1.21.0-debug AS kaniko

FROM docker.io/busybox:latest AS busybox

ENV SSL_CERT_DIR=/kaniko/ssl/certs
ENV PATH=/bin:/usr/local/bin:/kaniko

COPY --from=kaniko /kaniko /kaniko

ENTRYPOINT ["/bin/sh"]
