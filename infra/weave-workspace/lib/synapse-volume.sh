#!/usr/bin/env bash
# shellcheck shell=bash

# Shared guardrails for the local/dev Synapse data volume. These helpers are
# sourced by install.sh and operator-check.sh; they intentionally scope all
# checks to the Weave-local `weave-synapse` container and `weave_synapse_data`
# volume, never to any unrelated homelab Synapse instance.

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
  printf '%s\n' "${TF_VAR_synapse_volume_name:-weave_synapse_data}"
}

synapse_container_name() {
  printf '%s\n' "${TF_VAR_synapse_container_name:-weave-synapse}"
}

synapse_image_name() {
  printf '%s\n' "${TF_VAR_synapse_image:-matrixdotorg/synapse:v1.136.0}"
}

synapse_uid() {
  printf '%s\n' "${TF_VAR_synapse_uid:-991}"
}

synapse_gid() {
  printf '%s\n' "${TF_VAR_synapse_gid:-991}"
}

synapse_matrix_public_host() {
  printf '%s.%s\n' "${TF_VAR_matrix_subdomain:-matrix}" "${TF_VAR_tenant_domain:-weave.local}"
}

synapse_signing_key_path() {
  printf '/data/%s.signing.key\n' "$(synapse_matrix_public_host)"
}

synapse_docker_volume_exists() {
  docker volume inspect "$(synapse_volume_name)" >/dev/null 2>&1
}

synapse_terraform_state_has() {
  local address="$1"
  terraform -chdir="${INFRA_DIR}" state show "${address}" >/dev/null 2>&1
}

synapse_terraform_state_rm_if_present() {
  local address="$1"

  if synapse_terraform_state_has "${address}"; then
    terraform -chdir="${INFRA_DIR}" state rm "${address}" >/dev/null
  fi
}

synapse_reconcile_terraform_state() {
  local volume
  volume="$(synapse_volume_name)"

  [[ -n "${INFRA_DIR:-}" ]] || synapse_volume_fail "Synapse volume guard requires INFRA_DIR to point at the Terraform infrastructure stage."

  if synapse_docker_volume_exists; then
    if ! synapse_terraform_state_has module.matrix.docker_volume.synapse_data; then
      synapse_volume_log "Importing existing Docker volume ${volume} into Terraform state before bootstrap..."
      terraform -chdir="${INFRA_DIR}" import -input=false module.matrix.docker_volume.synapse_data "${volume}"
      # The existing volume may have been Docker-created outside Terraform. Force
      # the permission provisioner to run on the next apply instead of trusting
      # any stale provisioner state.
      synapse_terraform_state_rm_if_present module.matrix.terraform_data.synapse_volume_permissions
    fi
    return
  fi

  if synapse_terraform_state_has module.matrix.docker_volume.synapse_data || \
    synapse_terraform_state_has module.matrix.terraform_data.synapse_volume_permissions; then
    synapse_volume_log "Synapse Docker volume ${volume} is missing while Terraform state still records it; removing stale state so Terraform recreates it and reruns the permission guard."
    synapse_terraform_state_rm_if_present module.matrix.terraform_data.synapse_volume_permissions
    synapse_terraform_state_rm_if_present module.matrix.docker_volume.synapse_data
  fi
}

synapse_repair_volume_permissions() {
  local volume image uid gid signing_key
  volume="$(synapse_volume_name)"
  image="$(synapse_image_name)"
  uid="$(synapse_uid)"
  gid="$(synapse_gid)"
  signing_key="$(synapse_signing_key_path)"

  if ! synapse_docker_volume_exists; then
    synapse_volume_log "Synapse Docker volume ${volume} does not exist yet; Terraform will create it during infrastructure apply."
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
    synapse_volume_fail "Operator check failed: ${volume} is not writable by weave-synapse uid:gid ${uid}:${gid}, so Synapse cannot create/update $(synapse_signing_key_path). This check only targets weave-synapse, not homelab-synapse. Run ./install.sh to reconcile stale Terraform state and repair the volume before restarting Synapse."
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
