# MinIO Community and mc are built from their archived upstream source. The
# former minio/minio registry digest is no longer available; no third-party
# repackaged binary is trusted by this optional E2E profile.
FROM --platform=$BUILDPLATFORM golang:1.26.2-bookworm@sha256:47ce5636e9936b2c5cbf708925578ef386b4f8872aec74a67bd13a627d242b19 AS build

ARG TARGETOS
ARG TARGETARCH
ARG MINIO_REVISION=9e49d5e7a648f00e26f2246f4dc28e6b07f8c84a
ARG MC_REVISION=77f82e18b5401a65958f1619df6ebb994634bd88

RUN git clone --depth 1 --branch RELEASE.2025-10-15T17-29-55Z \
      https://github.com/minio/minio.git /src/minio \
    && test "$(git -C /src/minio rev-parse HEAD)" = "$MINIO_REVISION" \
    && git clone --depth 1 https://github.com/minio/mc.git /src/mc \
    && test "$(git -C /src/mc rev-parse HEAD)" = "$MC_REVISION"

RUN mkdir -p /out && cd /src/minio \
    && CGO_ENABLED=0 GOOS="$TARGETOS" GOARCH="$TARGETARCH" \
      go build -trimpath -tags kqueue \
      -ldflags="$(MINIO_RELEASE=RELEASE go run buildscripts/gen-ldflags.go 2025-10-15T17:29:55Z)" \
      -o /out/minio .
RUN cd /src/mc \
    && CGO_ENABLED=0 GOOS="$TARGETOS" GOARCH="$TARGETARCH" \
      go build -trimpath -tags kqueue \
      -ldflags="$(MC_RELEASE=SOURCE go run buildscripts/gen-ldflags.go)" \
      -o /out/mc .

FROM alpine:3.22.2@sha256:4b7ce07002c69e8f3d704a9c5d6fd3053be500b7f1c69fc0d80990c2ad8dd412

ARG MINIO_REVISION=9e49d5e7a648f00e26f2246f4dc28e6b07f8c84a
ARG MC_REVISION=77f82e18b5401a65958f1619df6ebb994634bd88
ARG WEAVE_BUILD_DEFINITION_DIGEST
LABEL org.opencontainers.image.title="Weave E2E MinIO Community source build" \
      org.opencontainers.image.source="https://github.com/minio/minio" \
      org.opencontainers.image.revision="$MINIO_REVISION" \
      org.opencontainers.image.licenses="AGPL-3.0-or-later" \
      com.massimotter.weave.mc-source-revision="$MC_REVISION" \
      com.massimotter.weave.build-definition-digest="$WEAVE_BUILD_DEFINITION_DIGEST"

COPY --from=build /out/minio /usr/local/bin/minio
COPY --from=build /out/mc /usr/local/bin/mc
COPY --from=build /src/minio/LICENSE /licenses/minio/LICENSE
COPY --from=build /src/minio/CREDITS /licenses/minio/CREDITS
COPY --from=build /src/mc/LICENSE /licenses/mc/LICENSE
COPY --from=build --chmod=755 /src/minio/dockerscripts/docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
COPY --from=build /etc/ssl/certs/ca-certificates.crt /etc/ssl/certs/ca-certificates.crt

EXPOSE 9000
VOLUME ["/data"]
ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
CMD ["minio", "server", "/data"]
