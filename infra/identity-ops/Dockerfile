ARG OPENTOFU_VERSION=1.10.6
ARG OPENTOFU_MINIMAL_DIGEST=sha256:49260f8a60850d0873d8a7496fdd40348bb4ce7e4977e5af85785e7f493322f0
ARG ALPINE_VERSION=3.22.1
ARG ALPINE_DIGEST=sha256:4bcff63911fcb4448bd4fdacec207030997caf25e9bea4045fa6c8c44de311d1

FROM ghcr.io/opentofu/opentofu:${OPENTOFU_VERSION}-minimal@${OPENTOFU_MINIMAL_DIGEST} AS opentofu

FROM alpine:${ALPINE_VERSION}@${ALPINE_DIGEST} AS runtime-base
RUN apk add --no-cache ca-certificates \
    && addgroup --system --gid 65532 weave \
    && adduser --system --disabled-password --no-create-home --uid 65532 \
        --ingroup weave weave
COPY --from=opentofu /usr/local/bin/tofu /usr/local/bin/tofu

FROM runtime-base AS provider-cache
WORKDIR /opt/weave/identity
COPY infra/weave-workspace/02-keycloak-setup/.terraform.lock.hcl ./
COPY infra/weave-workspace/02-keycloak-setup/*.tf ./
COPY infra/weave-workspace/02-keycloak-setup/modules ./modules
RUN mkdir -p /opt/weave/provider-cache \
    && tofu init -backend=false -input=false -lockfile=readonly \
    && tofu providers mirror /opt/weave/provider-cache \
    && rm -rf .terraform

FROM runtime-base
ARG OPENTOFU_VERSION
ARG ALPINE_VERSION
ARG WEAVE_IMAGE_CREATED=unbound
ARG WEAVE_IMAGE_REVISION=unbound
ARG WEAVE_IMAGE_VERSION=unbound
ARG WEAVE_SPEC_DIGEST=unbound
ARG WEAVE_SBOM_REFERENCE=unbound
ARG WEAVE_PROVENANCE_REFERENCE=unbound

LABEL org.opencontainers.image.title="Weave Identity Ops" \
      org.opencontainers.image.description="Reviewable Keycloak desired-state plan and apply runner without human identity fixtures" \
      org.opencontainers.image.source="https://github.com/masssi164/weave" \
      org.opencontainers.image.revision="${WEAVE_IMAGE_REVISION}" \
      org.opencontainers.image.version="${WEAVE_IMAGE_VERSION}" \
      org.opencontainers.image.created="${WEAVE_IMAGE_CREATED}" \
      org.opencontainers.image.licenses="Apache-2.0" \
      org.opencontainers.image.vendor="Weave" \
      com.massimotter.weave.spec-digest="${WEAVE_SPEC_DIGEST}" \
      com.massimotter.weave.module="identity-ops" \
      com.massimotter.weave.runtime-user="65532:65532" \
      com.massimotter.weave.dependency-platform="alpine-${ALPINE_VERSION}-opentofu-${OPENTOFU_VERSION}-keycloak-provider" \
      com.massimotter.weave.sbom-reference="${WEAVE_SBOM_REFERENCE}" \
      com.massimotter.weave.provenance-reference="${WEAVE_PROVENANCE_REFERENCE}"

COPY --from=provider-cache /opt/weave/identity /opt/weave/identity
COPY --from=provider-cache /opt/weave/provider-cache /opt/weave/provider-cache
COPY infra/identity-ops/identity-ops.sh /usr/local/bin/weave-identity-ops

RUN chmod -R a=rX /opt/weave/identity /opt/weave/provider-cache \
    && chmod 0555 /usr/local/bin/weave-identity-ops \
    && mkdir -p /state \
    && chown weave:weave /state

USER 65532:65532
WORKDIR /tmp
ENTRYPOINT ["/usr/local/bin/weave-identity-ops"]
CMD ["plan"]
