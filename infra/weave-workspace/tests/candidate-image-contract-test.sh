#!/usr/bin/env bash
# shellcheck shell=bash
# shellcheck disable=SC2016

set -euo pipefail

REPOSITORY_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../.." && pwd)"
readonly REPOSITORY_ROOT
readonly SERVER_IMAGE="${REPOSITORY_ROOT}/server/Dockerfile"
readonly MCP_IMAGE="${REPOSITORY_ROOT}/weave-mcp-server/Dockerfile"
readonly IDENTITY_OPS_IMAGE="${REPOSITORY_ROOT}/infra/identity-ops/Dockerfile"
readonly WORKFLOW="${REPOSITORY_ROOT}/.github/workflows/candidate-images.yml"

fail() {
  printf 'candidate image contract failed: %s\n' "$*" >&2
  exit 1
}

contains() {
  local file="$1"
  local text="$2"
  grep -Fq -- "${text}" "${file}" || fail "${file} omitted ${text}"
}

for image in "${SERVER_IMAGE}" "${MCP_IMAGE}" "${IDENTITY_OPS_IMAGE}"; do
  for label in \
    org.opencontainers.image.title \
    org.opencontainers.image.source \
    org.opencontainers.image.revision \
    org.opencontainers.image.version \
    org.opencontainers.image.created \
    org.opencontainers.image.licenses \
    org.opencontainers.image.vendor \
    com.massimotter.weave.spec-digest \
    com.massimotter.weave.module \
    com.massimotter.weave.runtime-user \
    com.massimotter.weave.dependency-platform \
    com.massimotter.weave.sbom-reference \
    com.massimotter.weave.provenance-reference; do
    contains "${image}" "${label}"
  done
done

contains "${SERVER_IMAGE}" 'USER 10001:10001'
contains "${SERVER_IMAGE}" 'com.massimotter.weave.module="server"'
contains "${MCP_IMAGE}" 'USER 10001:10001'
contains "${MCP_IMAGE}" 'com.massimotter.weave.module="weave-mcp-server"'
contains "${IDENTITY_OPS_IMAGE}" 'USER 65532:65532'
contains "${IDENTITY_OPS_IMAGE}" 'com.massimotter.weave.module="identity-ops"'

contains "${WORKFLOW}" 'tag_sha=sha-$GITHUB_SHA'
contains "${WORKFLOW}" 'tag_run=candidate-$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT'
contains "${WORKFLOW}" 'provenance: mode=max'
contains "${WORKFLOW}" 'sbom: true'
contains "${WORKFLOW}" 'Verify published OCI runtime metadata'
contains "${WORKFLOW}" 'validate_published_image'
contains "${WORKFLOW}" 'docker image inspect'
contains "${WORKFLOW}" 'com.massimotter.weave.runtime-user'
if grep -Fq ':latest' "${WORKFLOW}"; then
  fail "${WORKFLOW} contains a mutable latest tag"
fi

printf 'candidate image contract tests passed\n'
