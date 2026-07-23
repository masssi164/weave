ARG KEYCLOAK_VERSION=26.7.0

FROM maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237 AS extension
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress -Dmaven.test.skip=true package

FROM quay.io/keycloak/keycloak:26.7.0@sha256:2eb3cd316835c990e69e26ade292ffa78f6fb0db7d5fc6377463c162e1979ac0 AS builder
COPY --from=extension /workspace/target/weave-keycloak-event-listener-1.0.0.jar /opt/keycloak/providers/weave-keycloak-event-listener.jar
COPY themes/weave /opt/keycloak/themes/weave
RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:26.7.0@sha256:2eb3cd316835c990e69e26ade292ffa78f6fb0db7d5fc6377463c162e1979ac0
ARG KEYCLOAK_VERSION
COPY --from=builder /opt/keycloak/ /opt/keycloak/
LABEL org.opencontainers.image.title="Weave Keycloak"
LABEL org.opencontainers.image.description="Version-pinned Keycloak with the minimal Weave organization-membership event bridge"
LABEL com.massimotter.weave.keycloak.version="${KEYCLOAK_VERSION}"
