FROM eclipse-temurin:21-jre-noble@sha256:373787d1d45a87f084fda43e7de0e9acf5eedee049446efac738f13587ec4c64 AS public-roots

FROM ubuntu:24.04@sha256:fe81560990fae86dc4743202b2cb4d3df98d0bcf949173cecef15ce1127ef852

ARG WEAVE_IMAGE_REVISION
ARG WEAVE_IMAGE_CREATED
ARG WEAVE_IMAGE_VERSION
ARG WEAVE_SPEC_DIGEST
LABEL org.opencontainers.image.source="https://github.com/masssi164/weave" \
      org.opencontainers.image.revision=$WEAVE_IMAGE_REVISION \
      org.opencontainers.image.created=$WEAVE_IMAGE_CREATED \
      org.opencontainers.image.version=$WEAVE_IMAGE_VERSION \
      org.opencontainers.image.title="Weave managed OpenLDAP" \
      org.opencontainers.image.licenses="EUPL-1.2 AND OLDAP-2.8" \
      com.massimotter.weave.spec-digest=$WEAVE_SPEC_DIGEST

COPY --from=public-roots /etc/ssl/certs/ca-certificates.crt /etc/ssl/certs/ca-certificates.crt

RUN printf 'slapd slapd/no_configuration boolean true\n' | debconf-set-selections \
    && sed -i 's|http://archive.ubuntu.com/ubuntu/|https://snapshot.ubuntu.com/ubuntu/20260828T000000Z/|g; s|http://ports.ubuntu.com/ubuntu-ports/|https://snapshot.ubuntu.com/ubuntu/20260828T000000Z/|g; s|http://security.ubuntu.com/ubuntu/|https://snapshot.ubuntu.com/ubuntu/20260828T000000Z/|g' /etc/apt/sources.list.d/ubuntu.sources \
    && apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
       slapd=2.6.10+dfsg-0ubuntu0.24.04.1 \
       ldap-utils=2.6.10+dfsg-0ubuntu0.24.04.1 python3 ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /var/lib/ldap /etc/ldap/slapd.d /run/ldap \
    && chmod 0770 /var/lib/ldap /etc/ldap/slapd.d /run/ldap

COPY infra/weave-workspace/ldap/*.py infra/weave-workspace/ldap/*.ldif infra/weave-workspace/ldap/slapd.conf /opt/weave/ldap/
USER 1000:0
EXPOSE 1636
ENTRYPOINT ["python3", "/opt/weave/ldap/serve.py"]
