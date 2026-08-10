FROM rust:1.95.0-slim-bookworm@sha256:d7482085ff5b415f84dba5647ae71606650bdef00db7aeb69f4b3d170c3e4082 AS matrix-protocol
WORKDIR /source
COPY Cargo.toml Cargo.lock ./
COPY rust/matrix-protocol/Cargo.toml rust/matrix-protocol/Cargo.toml
COPY rust/matrix-protocol/src rust/matrix-protocol/src
COPY rust/matrix-client/Cargo.toml rust/matrix-client/Cargo.toml
COPY rust/matrix-client/src rust/matrix-client/src
COPY rust/vendor rust/vendor
RUN cargo build --release -p weave-matrix-protocol --features jni

FROM eclipse-temurin:21-jre-noble@sha256:373787d1d45a87f084fda43e7de0e9acf5eedee049446efac738f13587ec4c64
WORKDIR /app

RUN groupadd --gid 10001 weave \
    && useradd --uid 10001 --gid weave --no-create-home --home-dir /nonexistent --shell /usr/sbin/nologin weave \
    && install -d -o weave -g weave -m 0755 /app/lib

ARG WEAVE_IMAGE_CREATED=unbound
ARG WEAVE_IMAGE_REVISION=unbound
ARG WEAVE_IMAGE_VERSION=unbound
ARG WEAVE_SPEC_DIGEST=unbound
ARG WEAVE_SBOM_REFERENCE=unbound
ARG WEAVE_PROVENANCE_REFERENCE=unbound
LABEL org.opencontainers.image.title="Weave Server" \
      org.opencontainers.image.description="Canonical Weave collaboration domains, policy, persistence, and open-standard projections" \
      org.opencontainers.image.source="https://github.com/masssi164/weave" \
      org.opencontainers.image.revision="${WEAVE_IMAGE_REVISION}" \
      org.opencontainers.image.version="${WEAVE_IMAGE_VERSION}" \
      org.opencontainers.image.created="${WEAVE_IMAGE_CREATED}" \
      org.opencontainers.image.licenses="Apache-2.0" \
      org.opencontainers.image.vendor="Weave" \
      com.massimotter.weave.spec-digest="${WEAVE_SPEC_DIGEST}" \
      com.massimotter.weave.module="server" \
      com.massimotter.weave.runtime-user="10001:10001" \
      com.massimotter.weave.dependency-platform="java21-spring-boot-4.1" \
      com.massimotter.weave.sbom-reference="${WEAVE_SBOM_REFERENCE}" \
      com.massimotter.weave.provenance-reference="${WEAVE_PROVENANCE_REFERENCE}"

ARG JAR_FILE=server/build/libs/*.jar
COPY --chown=10001:10001 ${JAR_FILE} /app/app.jar
COPY --chown=10001:10001 --from=matrix-protocol /source/target/release/libweave_matrix_protocol.so /app/lib/libweave_matrix_protocol.so

ENV WEAVE_MATRIX_PROTOCOL_LIBRARY_PATH=/app/lib/libweave_matrix_protocol.so

EXPOSE 8080
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
