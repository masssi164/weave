#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

: "${WEAVE_IAC_BIN:=tofu}"
export WEAVE_IAC_BIN

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly ROOT_DIR
readonly INFRA_DIR="${ROOT_DIR}/01-infrastructure"
readonly KEYCLOAK_DIR="${ROOT_DIR}/02-keycloak-setup"
readonly BOOTSTRAP_ENV_FILE="${ROOT_DIR}/.generated/bootstrap.env"
readonly RUNNER_BOOTSTRAP_ENV_FILE="/tmp/weave-infra/weave-workspace/.generated/bootstrap.env"
readonly SYNAPSE_VOLUME_HELPER="${ROOT_DIR}/lib/synapse-volume.sh"
readonly WEAVE_CONTAINERS=(
  weave-proxy
  weave-keycloak
  weave-backend
  weave-mas
  weave-synapse
  weave-nextcloud
  weave-db
  weave-mcp-server
  weave-mailpit
)
readonly WEAVE_VOLUMES=(
  weave_caddy_data
  weave_caddy_config
  weave_db_data
  weave_keycloak_data
  weave_mailpit_data
  weave_nextcloud_data
  weave_synapse_data
  weave_matrix_chat_appservice_runtime
)
readonly DESTRUCTIVE_DATA_DOMAINS=(
  "Keycloak identity/session data"
  "Mailpit messages containing dogfood activation links"
  "Weave backend service data stored in Postgres"
  "Matrix/Synapse database and media state"
  "Matrix Chat Application Service registration/token runtime"
  "Nextcloud database, files, and calendar data"
  "Shared Postgres service databases"
  "Caddy/TLS state stored in Docker volumes"
)
readonly BACKUP_GUIDANCE="docs/operator-runbook.md#5-backup-expectations"
readonly LEGACY_CONFIRMATION="weave-delete-local-data"
CHAT_E2E_PROOF_CLEANUP_ARMED=false

log() {
  printf '%s\n' "$*"
}

fail() {
  printf '%s\n' "$*" >&2
  exit 1
}

dry_run_enabled() {
  [[ "${WEAVE_TEARDOWN_DRY_RUN:-false}" == "true" ]]
}

required_destructive_confirmation() {
  printf '%s' "${TF_VAR_tenant_slug:-weave}"
}

iac_destroy() {
  local dir="$1"

  if [[ ! -d "${dir}" ]]; then
    return
  fi

  if dry_run_enabled; then
    log "DRY RUN: would run ${WEAVE_IAC_BIN} destroy in ${dir}"
    return
  fi

  "${WEAVE_IAC_BIN}" -chdir="${dir}" init -input=false >/dev/null 2>&1 || true
  "${WEAVE_IAC_BIN}" -chdir="${dir}" destroy -refresh=false -input=false -auto-approve || true
}

remove_container() {
  local name="$1"

  if dry_run_enabled; then
    log "DRY RUN: would remove container ${name}"
    return
  fi

  if docker container inspect "${name}" >/dev/null 2>&1; then
    log "Removing container ${name}"
    docker rm -f -v "${name}" >/dev/null 2>&1 || true
  fi
}

remove_volume() {
  local name="$1"

  if dry_run_enabled; then
    log "DRY RUN: would remove volume ${name}"
    return
  fi

  if docker volume inspect "${name}" >/dev/null 2>&1; then
    log "Removing volume ${name}"
    docker volume rm -f "${name}" >/dev/null 2>&1 || true
  fi
}

forget_matrix_volume_terraform_state() {
  if dry_run_enabled; then
    log "DRY RUN: would remove stale OpenTofu state for Synapse data and Matrix Chat Application Service runtime volumes"
    return
  fi

  # shellcheck disable=SC1090
  source "${SYNAPSE_VOLUME_HELPER}"
  synapse_terraform_state_rm_if_present module.matrix.terraform_data.synapse_volume_permissions
  synapse_terraform_state_rm_if_present module.matrix.docker_volume.synapse_data
  synapse_terraform_state_rm_if_present module.matrix.terraform_data.appservice_runtime
  synapse_terraform_state_rm_if_present module.matrix.docker_volume.appservice_runtime
}

isolated_teardown_evidence_requested() {
  [[ -n "${WEAVE_TEARDOWN_EVIDENCE_FILE:-}" || -n "${WEAVE_CANDIDATE_COMMIT:-}" ]]
}

sha256_text() {
  printf '%s' "$1" | shasum -a 256 | awk '{print $1}'
}

verify_isolated_teardown_scope() {
  isolated_teardown_evidence_requested || return 0

  [[ -n "${WEAVE_TEARDOWN_EVIDENCE_FILE:-}" && -n "${WEAVE_CANDIDATE_COMMIT:-}" ]] ||
    fail "WEAVE_TEARDOWN_EVIDENCE_FILE and WEAVE_CANDIDATE_COMMIT must be set together."
  [[ "${WEAVE_TEARDOWN_DRY_RUN:-false}" != "true" ]] ||
    fail "Isolated teardown evidence cannot be emitted from a dry run."
  [[ "${WEAVE_E2E_STACK_SCOPE:-}" == "isolated" ]] ||
    fail "Isolated teardown evidence requires WEAVE_E2E_STACK_SCOPE=isolated."
  [[ "${TF_VAR_isolated_e2e_enabled:-false}" == "true" ]] ||
    fail "Isolated teardown evidence requires TF_VAR_isolated_e2e_enabled=true."
  [[ "${TF_VAR_isolated_e2e_namespace:-}" =~ ^weave-e2e-[a-z0-9][a-z0-9-]{5,47}$ ]] ||
    fail "Isolated teardown evidence requires a bounded weave-e2e-* namespace."
  [[ "${TF_VAR_docker_network_name:-}" == "${TF_VAR_isolated_e2e_namespace}_network" ]] ||
    fail "Isolated teardown evidence requires the namespace-owned Docker network."
  [[ "${WEAVE_CANDIDATE_COMMIT}" =~ ^[0-9a-f]{40,64}$ ]] ||
    fail "WEAVE_CANDIDATE_COMMIT must be a full lowercase hexadecimal commit identifier."
  [[ -f "${WEAVE_E2E_IDENTITY_MANIFEST_PATH:-}" ]] ||
    fail "Isolated teardown evidence requires WEAVE_E2E_IDENTITY_MANIFEST_PATH."
  command -v jq >/dev/null 2>&1 || fail "Missing required command for isolated teardown evidence: jq"
  command -v shasum >/dev/null 2>&1 || fail "Missing required command for isolated teardown evidence: shasum"

  local manifest_namespace_hash
  manifest_namespace_hash="$(jq -er '.namespaceSha256 | select(type == "string" and test("^[0-9a-f]{64}$"))' "${WEAVE_E2E_IDENTITY_MANIFEST_PATH}")" ||
    fail "Identity manifest does not contain a support-safe namespaceSha256."
  [[ "${manifest_namespace_hash}" == "$(sha256_text "${TF_VAR_isolated_e2e_namespace}")" ]] ||
    fail "Identity manifest namespace hash does not match the isolated runtime namespace."
  [[ "${WEAVE_REMOVE_VOLUMES:-false}" == "true" ]] ||
    fail "Isolated teardown evidence requires WEAVE_REMOVE_VOLUMES=true so provider data cannot survive."
  [[ "${WEAVE_CONFIRM_DESTRUCTIVE_RESET:-}" == "$(required_destructive_confirmation)" ]] ||
    fail "Isolated teardown evidence requires the exact typed disposable tenant confirmation."
  [[ "${TF_VAR_chat_e2e_proof_enabled:-false}" == "true" ]] ||
    fail "Isolated teardown evidence requires the run-scoped Chat provider proof boundary."
  [[ "${TF_VAR_chat_e2e_proof_run_id:-}" == "${WEAVE_E2E_RUN_ID:-}" ]] ||
    fail "Chat provider proof teardown run binding does not match the exact isolated run."
  [[ -n "${TF_VAR_chat_e2e_proof_token_host_path:-}" ]] ||
    fail "Chat provider proof teardown credential path is missing."
  [[ "$(basename -- "${TF_VAR_chat_e2e_proof_token_host_path}")" == "chat-provider-proof.token" &&
     "$(basename -- "$(dirname -- "${TF_VAR_chat_e2e_proof_token_host_path}")")" == "${TF_VAR_isolated_e2e_namespace}" ]] ||
    fail "Chat provider proof teardown credential is outside the isolated namespace."
  [[ -f "${TF_VAR_chat_e2e_proof_token_host_path}" && ! -L "${TF_VAR_chat_e2e_proof_token_host_path}" ]] ||
    fail "Chat provider proof teardown credential is not a regular private file."
  CHAT_E2E_PROOF_CLEANUP_ARMED=true

  local container_name
  local attached_networks
  for container_name in "${WEAVE_CONTAINERS[@]}"; do
    if ! docker container inspect "${container_name}" >/dev/null 2>&1; then
      continue
    fi
    attached_networks="$(docker inspect --format '{{range $name, $_ := .NetworkSettings.Networks}}{{$name}}{{"\n"}}{{end}}' "${container_name}")"
    printf '%s\n' "${attached_networks}" | grep -Fxq "${TF_VAR_docker_network_name}" ||
      fail "Refusing isolated teardown evidence: ${container_name} is not attached to the namespace-owned network."
  done
}

resource_count() {
  local kind="$1"
  local name="$2"
  if docker "${kind}" inspect "${name}" >/dev/null 2>&1; then
    printf '1'
  else
    printf '0'
  fi
}

write_isolated_teardown_evidence() {
  isolated_teardown_evidence_requested || return 0

  local containers_json='{}'
  local networks_json='{}'
  local volumes_json='{}'
  local total=0
  local count
  local resource_name

  for resource_name in "${WEAVE_CONTAINERS[@]}"; do
    count="$(resource_count container "${resource_name}")"
    total=$((total + count))
    containers_json="$(jq --arg name "${resource_name}" --argjson count "${count}" '. + {($name): $count}' <<<"${containers_json}")"
  done

  count="$(resource_count network "${TF_VAR_docker_network_name}")"
  total=$((total + count))
  networks_json="$(jq --arg name "${TF_VAR_docker_network_name}" --argjson count "${count}" '. + {($name): $count}' <<<"${networks_json}")"

  for resource_name in "${WEAVE_VOLUMES[@]}"; do
    count="$(resource_count volume "${resource_name}")"
    total=$((total + count))
    volumes_json="$(jq --arg name "${resource_name}" --argjson count "${count}" '. + {($name): $count}' <<<"${volumes_json}")"
  done

  local namespace_hash
  namespace_hash="$(jq -r '.namespaceSha256' "${WEAVE_E2E_IDENTITY_MANIFEST_PATH}")"
  mkdir -p "$(dirname -- "${WEAVE_TEARDOWN_EVIDENCE_FILE}")"
  jq -n \
    --arg candidateCommit "${WEAVE_CANDIDATE_COMMIT}" \
    --arg namespaceSha256 "${namespace_hash}" \
    --argjson containers "${containers_json}" \
    --argjson networks "${networks_json}" \
    --argjson volumes "${volumes_json}" \
    --argjson remainingOwnedResources "${total}" \
    '{
      schema:"weave.isolated-stack-teardown.v1",
      candidateCommit:$candidateCommit,
      namespaceSha256:$namespaceSha256,
      isolatedRuntimeVerified:true,
      postRemovalCounts:{containers:$containers,networks:$networks,volumes:$volumes,remainingOwnedResources:$remainingOwnedResources},
      providerNamespaceDestroyed:($remainingOwnedResources == 0),
      chatProofCredentialDestroyed:true,
      persistentDogfoodTouched:false,
      credentialsIncluded:false,
      rawProviderPayloadIncluded:false,
      supportSafe:true
    }' >"${WEAVE_TEARDOWN_EVIDENCE_FILE}"
  chmod 0644 "${WEAVE_TEARDOWN_EVIDENCE_FILE}"

  [[ "${total}" == "0" ]] ||
    fail "Isolated teardown left ${total} owned Docker resource(s); see the support-safe teardown evidence."
}

remove_chat_e2e_proof_credential() {
  [[ "${CHAT_E2E_PROOF_CLEANUP_ARMED}" == "true" ]] || return 0
  rm -f -- "${TF_VAR_chat_e2e_proof_token_host_path}"
  [[ ! -e "${TF_VAR_chat_e2e_proof_token_host_path}" ]] ||
    fail "Isolated teardown could not remove the Chat provider proof credential."
  CHAT_E2E_PROOF_CLEANUP_ARMED=false
  log "Removed isolated Chat provider proof credential."
}

on_teardown_exit() {
  local status=$?
  trap - EXIT INT TERM
  if [[ "${CHAT_E2E_PROOF_CLEANUP_ARMED}" == "true" ]]; then
    rm -f -- "${TF_VAR_chat_e2e_proof_token_host_path:-}" || status=1
  fi
  exit "${status}"
}

remove_network() {
  local network_name="${TF_VAR_docker_network_name:-weave_network}"

  if dry_run_enabled; then
    log "DRY RUN: would remove network ${network_name}"
    return
  fi

  if docker network inspect "${network_name}" >/dev/null 2>&1; then
    log "Removing network ${network_name}"
    docker network rm "${network_name}" >/dev/null 2>&1 || true
  fi
}

print_destructive_reset_scope() {
  local required_confirmation
  required_confirmation="$(required_destructive_confirmation)"

  cat >&2 <<EOF
Destructive Weave local/dev reset requested.

Before deleting persistent data, read backup/restore guidance:
  ${BACKUP_GUIDANCE}

Affected data domains:
EOF

  local domain
  for domain in "${DESTRUCTIVE_DATA_DOMAINS[@]}"; do
    printf '  - %s\n' "${domain}" >&2
  done

  cat >&2 <<EOF

Docker volumes scheduled for deletion:
EOF

  local volume
  for volume in "${WEAVE_VOLUMES[@]}"; do
    printf '  - %s\n' "${volume}" >&2
  done

  cat >&2 <<EOF

Generated local secrets/config in .generated/ are not removed by this helper;
back them up separately before deleting them manually.

Required confirmation:
  WEAVE_REMOVE_VOLUMES=true
  WEAVE_CONFIRM_DESTRUCTIVE_RESET=${required_confirmation}
EOF
}

confirm_volume_removal() {
  local required_confirmation
  required_confirmation="$(required_destructive_confirmation)"

  if [[ "${WEAVE_REMOVE_VOLUMES:-false}" != "true" ]]; then
    log "Persistent Docker volumes: preserved. Set WEAVE_REMOVE_VOLUMES=true plus WEAVE_CONFIRM_DESTRUCTIVE_RESET=${required_confirmation} only after taking a backup."
    return 1
  fi

  print_destructive_reset_scope

  if [[ "${WEAVE_CONFIRM_REMOVE_VOLUMES:-}" == "${LEGACY_CONFIRMATION}" && -z "${WEAVE_CONFIRM_DESTRUCTIVE_RESET:-}" ]]; then
    cat >&2 <<EOF

Refusing to remove persistent Weave Docker volumes: the old
WEAVE_CONFIRM_REMOVE_VOLUMES=${LEGACY_CONFIRMATION} confirmation is no longer
accepted. Type the tenant/workspace slug instead.
EOF
    exit 2
  fi

  if [[ "${WEAVE_CONFIRM_DESTRUCTIVE_RESET:-}" == "${required_confirmation}" ]]; then
    log "Destructive reset confirmed for tenant/workspace slug '${required_confirmation}'."
    return 0
  fi

  cat >&2 <<EOF

Refusing to remove persistent Weave Docker volumes without the typed tenant/workspace confirmation.

Container/network cleanup is safe by default and has already been requested. To
also delete local data volumes, rerun with both:

  WEAVE_REMOVE_VOLUMES=true
  WEAVE_CONFIRM_DESTRUCTIVE_RESET=${required_confirmation}

Do not run the destructive form until the backup guidance above has been reviewed.
EOF
  exit 2
}

load_bootstrap_env() {
  local env_file=""
  local index
  local var_name
  local -a protected_names=(
    WEAVE_CANDIDATE_COMMIT
    WEAVE_CONFIRM_DESTRUCTIVE_RESET
    WEAVE_E2E_IDENTITY_MANIFEST_PATH
    WEAVE_E2E_RUN_ID
    WEAVE_E2E_STACK_SCOPE
    WEAVE_REMOVE_VOLUMES
    WEAVE_TEARDOWN_EVIDENCE_FILE
    TF_VAR_docker_network_name
    TF_VAR_isolated_e2e_enabled
    TF_VAR_isolated_e2e_namespace
    TF_VAR_chat_e2e_proof_enabled
    TF_VAR_chat_e2e_proof_token_host_path
    TF_VAR_chat_e2e_proof_run_id
    TF_VAR_tenant_slug
  )
  local -a preset_names=()
  local -a preset_values=()

  if [[ -f "${BOOTSTRAP_ENV_FILE}" ]]; then
    env_file="${BOOTSTRAP_ENV_FILE}"
  elif [[ -f "${RUNNER_BOOTSTRAP_ENV_FILE}" ]]; then
    env_file="${RUNNER_BOOTSTRAP_ENV_FILE}"
  fi

  if [[ -n "${env_file}" ]]; then
    for var_name in "${protected_names[@]}"; do
      if [[ "${!var_name+x}" == "x" ]]; then
        preset_names+=("${var_name}")
        preset_values+=("${!var_name}")
      fi
    done
    # shellcheck disable=SC1090
    source "${env_file}"
    for ((index = 0; index < ${#preset_names[@]}; index++)); do
      export "${preset_names[$index]}=${preset_values[$index]}"
    done
  fi
}

require_runtime_commands() {
  if dry_run_enabled; then
    return
  fi

  command -v docker >/dev/null 2>&1 || {
    printf 'Missing required command: docker\n' >&2
    exit 1
  }

  command -v "${WEAVE_IAC_BIN}" >/dev/null 2>&1 || {
    printf 'Missing required command: %s (OpenTofu/tofu by default)\n' "${WEAVE_IAC_BIN}" >&2
    exit 1
  }
}

main() {
  require_runtime_commands
  load_bootstrap_env
  verify_isolated_teardown_scope
  trap on_teardown_exit EXIT
  trap 'exit 130' INT
  trap 'exit 143' TERM

  if [[ "${WEAVE_IAC_DESTROY:-false}" == "true" ]]; then
    iac_destroy "${KEYCLOAK_DIR}"
    iac_destroy "${INFRA_DIR}"
  fi

  local container
  for container in "${WEAVE_CONTAINERS[@]}"; do
    remove_container "${container}"
  done

  remove_network

  if confirm_volume_removal; then
    local volume
    for volume in "${WEAVE_VOLUMES[@]}"; do
      remove_volume "${volume}"
    done
    forget_matrix_volume_terraform_state
  fi

  remove_chat_e2e_proof_credential
  write_isolated_teardown_evidence
  trap - EXIT INT TERM
}

main "$@"
