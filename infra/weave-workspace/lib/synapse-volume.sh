#!/usr/bin/env bash
# shellcheck shell=bash

# Shared guardrails for the local/dev Synapse data volume. These helpers are
# sourced by install.sh and operator-check.sh; they intentionally scope all
# checks to the selected Weave namespace, never to any unrelated homelab
# Synapse instance.

synapse_volume_log() {
  if declare -F log >/dev/null 2>&1; then
    log "$*"
  else
    printf '%s\n' "$*"
  fi
}

synapse_volume_fail() {
  if declare -F fail >/dev/null 2>&1; then
    fail "$*"
  fi
  printf '%s\n' "$*" >&2
  exit 1
}

synapse_volume_name() {
  if declare -F weave_volume_name >/dev/null 2>&1; then
    weave_volume_name synapse_data
    printf '\n'
    return
  fi
  printf 'weave_synapse_data\n'
}

synapse_container_name() {
  if declare -F weave_container_name >/dev/null 2>&1; then
    weave_container_name synapse
    printf '\n'
    return
  fi
  printf 'weave-synapse\n'
}

synapse_image_name() {
  printf '%s\n' "${WEAVE_SYNAPSE_IMAGE:-matrixdotorg/synapse:v1.136.0}"
}

synapse_uid() {
  printf '%s\n' "${WEAVE_SYNAPSE_UID:-991}"
}

synapse_gid() {
  printf '%s\n' "${WEAVE_SYNAPSE_GID:-991}"
}

synapse_matrix_public_host() {
  printf '%s.%s\n' "${WEAVE_MATRIX_SUBDOMAIN:-matrix}" "${WEAVE_TENANT_DOMAIN:-weave.test}"
}

synapse_signing_key_path() {
  printf '/data/%s.signing.key\n' "$(synapse_matrix_public_host)"
}

synapse_docker_volume_exists() {
  docker volume inspect "$(synapse_volume_name)" >/dev/null 2>&1
}

synapse_repair_volume_permissions() {
  local volume image uid gid signing_key
  volume="$(synapse_volume_name)"
  image="$(synapse_image_name)"
  uid="$(synapse_uid)"
  gid="$(synapse_gid)"
  signing_key="$(synapse_signing_key_path)"

  if ! synapse_docker_volume_exists; then
    synapse_volume_log "Synapse Docker volume ${volume} does not exist yet; Compose prepare will create the owned volume."
    return
  fi

  synapse_volume_log "Repairing ${volume} ownership for weave-synapse (${uid}:${gid}) before Synapse starts..."
  docker run --rm -u 0:0 \
    -e SYNAPSE_UID="${uid}" \
    -e SYNAPSE_GID="${gid}" \
    -e SYNAPSE_SIGNING_KEY="${signing_key}" \
    -v "${volume}:/data" \
    --entrypoint /bin/sh \
    "${image}" \
    -c 'set -eu
        install -d -m 0750 -o "${SYNAPSE_UID}" -g "${SYNAPSE_GID}" /data /data/media_store
        chown -R "${SYNAPSE_UID}:${SYNAPSE_GID}" /data
        chmod 0750 /data /data/media_store
        if [ -e "${SYNAPSE_SIGNING_KEY}" ]; then
          chown "${SYNAPSE_UID}:${SYNAPSE_GID}" "${SYNAPSE_SIGNING_KEY}"
          chmod 0600 "${SYNAPSE_SIGNING_KEY}"
        fi' || \
    synapse_volume_fail "Failed to repair ${volume} for weave-synapse uid:gid ${uid}:${gid}. This guard only targets weave-synapse, not homelab-synapse."
}

synapse_verify_volume_writable() {
  local volume image uid gid signing_key check_file
  volume="$(synapse_volume_name)"
  image="$(synapse_image_name)"
  uid="$(synapse_uid)"
  gid="$(synapse_gid)"
  signing_key="$(synapse_signing_key_path)"
  check_file="${signing_key}.weave-writable-check"

  synapse_docker_volume_exists || synapse_volume_fail "Operator check failed: Weave-local Synapse volume ${volume} is missing. This check only targets weave-synapse, not homelab-synapse. Run ./install.sh to recreate/reconcile the Weave-local volume."

  docker run --rm -u "${uid}:${gid}" \
    -e SYNAPSE_SIGNING_KEY_CHECK="${check_file}" \
    -v "${volume}:/data" \
    --entrypoint /bin/sh \
    "${image}" \
    -c 'set -eu
        test -d /data
        test -w /data
        test -d /data/media_store
        test -w /data/media_store
        : > "${SYNAPSE_SIGNING_KEY_CHECK}"
        rm -f "${SYNAPSE_SIGNING_KEY_CHECK}"' || \
    synapse_volume_fail "Operator check failed: ${volume} is not writable by weave-synapse uid:gid ${uid}:${gid}, so Synapse cannot create/update $(synapse_signing_key_path). This check only targets weave-synapse, not homelab-synapse. Run ./install.sh to reconcile the Compose-owned volume before restarting Synapse."
}

synapse_print_volume_metadata() {
  local volume image metadata
  volume="$(synapse_volume_name)"
  image="$(synapse_image_name)"

  synapse_docker_volume_exists || synapse_volume_fail "Operator check failed: Weave-local Synapse volume ${volume} is missing. This check only targets weave-synapse, not homelab-synapse."

  metadata="$(docker run --rm -u 0:0 -v "${volume}:/data" --entrypoint /bin/sh "${image}" -c 'set -eu; stat -c "%u:%g %a %n" /data /data/media_store 2>/dev/null || stat -f "%u:%g %Lp %N" /data /data/media_store')"
  synapse_volume_log "Synapse volume metadata for weave-synapse (${volume}; not homelab-synapse):"
  synapse_volume_log "${metadata}"
}

synapse_operator_diagnose_volume() {
  synapse_volume_log "Checking weave-synapse data volume ownership/signing-key writability (does not inspect homelab-synapse)..."
  synapse_print_volume_metadata
  synapse_verify_volume_writable
}
