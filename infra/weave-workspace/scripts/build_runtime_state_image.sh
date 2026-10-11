#!/usr/bin/env bash
# Build only the optional disposable S3 E2E image from pinned upstream source.
set -euo pipefail

workspace_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
readonly workspace_root
readonly minio_revision=9e49d5e7a648f00e26f2246f4dc28e6b07f8c84a
readonly mc_revision=77f82e18b5401a65958f1619df6ebb994634bd88
definition_sha="$(shasum -a 256 "$workspace_root/runtime-state-image/Dockerfile" | awk '{print $1}')"
readonly definition_sha
readonly image_tag="weave-runtime-state:source-${minio_revision:0:12}-${mc_revision:0:12}-${definition_sha:0:12}"

if ! docker image inspect "$image_tag" >/dev/null 2>&1; then
  docker build \
    --platform linux/amd64 \
    --build-arg "WEAVE_BUILD_DEFINITION_DIGEST=sha256:$definition_sha" \
    --tag "$image_tag" \
    --file "$workspace_root/runtime-state-image/Dockerfile" \
    "$workspace_root/runtime-state-image" >&2
fi

minio_label="$(docker image inspect "$image_tag" --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}')"
mc_label="$(docker image inspect "$image_tag" --format '{{ index .Config.Labels "com.massimotter.weave.mc-source-revision" }}')"
license_label="$(docker image inspect "$image_tag" --format '{{ index .Config.Labels "org.opencontainers.image.licenses" }}')"
definition_label="$(docker image inspect "$image_tag" --format '{{ index .Config.Labels "com.massimotter.weave.build-definition-digest" }}')"
[[ "$minio_label" == "$minio_revision" && "$mc_label" == "$mc_revision" && "$license_label" == AGPL-3.0-or-later && "$definition_label" == "sha256:$definition_sha" ]] || {
  echo 'WEAVE_RUNTIME_STATE_IMAGE_ERROR source or licence label mismatch' >&2
  exit 1
}

docker image inspect "$image_tag" --format '{{.Id}}'
