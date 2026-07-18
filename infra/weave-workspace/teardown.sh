#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

: "${WEAVE_IAC_BIN:=tofu}"
export WEAVE_IAC_BIN

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly ROOT_DIR
readonly INFRA_DIR="${ROOT_DIR}/01-infrastructure"
readonly KEYCLOAK_DIR="${ROOT_DIR}/02-keycloak-setup"
readonly SYNAPSE_VOLUME_HELPER="${ROOT_DIR}/lib/synapse-volume.sh"
# shellcheck source=infra/weave-workspace/lib/runtime-namespace.sh
source "${ROOT_DIR}/lib/runtime-namespace.sh"

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
readonly IAC_DESTROY_MAX_ATTEMPTS=3
readonly IAC_DESTROY_RETRY_DELAY_SECONDS=2
CHAT_E2E_PROOF_CLEANUP_ARMED=false
IAC_DESTROY_FAILED=false

log() {
  printf '%s\n' "$*"
}

fail() {
  printf 'WEAVE_TEARDOWN_ERROR %s\n' "$*" >&2
  exit 1
}

dry_run_enabled() {
  [[ "${WEAVE_TEARDOWN_DRY_RUN:-false}" == "true" ]]
}

isolated_intent_detected() {
  [[ "${WEAVE_E2E_STACK_SCOPE:-}" == "isolated" ||
    "${TF_VAR_isolated_e2e_enabled:-false}" == "true" ||
    -n "${TF_VAR_isolated_e2e_namespace:-}" ||
    -n "${WEAVE_E2E_RUN_ID:-}" ||
    -n "${WEAVE_E2E_OUTPUT_ROOT:-}" ||
    -n "${WEAVE_TEARDOWN_OWNERSHIP_FILE:-}" ||
    -n "${WEAVE_TEARDOWN_EVIDENCE_FILE:-}" ]]
}

file_mode() {
  local path="$1"
  if stat -c '%a' "${path}" >/dev/null 2>&1; then
    stat -c '%a' "${path}"
  else
    stat -f '%Lp' "${path}"
  fi
}

require_isolated_ownership() {
  isolated_intent_detected || return 0

  [[ "${WEAVE_E2E_STACK_SCOPE:-}" == "isolated" ]] ||
    fail "isolated teardown intent requires WEAVE_E2E_STACK_SCOPE=isolated"
  [[ "${TF_VAR_isolated_e2e_enabled:-false}" == "true" ]] ||
    fail "isolated teardown requires TF_VAR_isolated_e2e_enabled=true"
  weave_validate_isolated_namespace || exit 1
  [[ "${TF_VAR_docker_network_name:-}" == "$(weave_network_name)" ]] ||
    fail "isolated teardown network does not match its run namespace"
  [[ -n "${WEAVE_E2E_RUN_ID:-}" ]] ||
    fail "isolated teardown requires a non-empty WEAVE_E2E_RUN_ID"
  [[ "${WEAVE_CANDIDATE_COMMIT:-}" =~ ^[0-9a-f]{40}$ ]] ||
    fail "isolated teardown requires a lowercase 40-character WEAVE_CANDIDATE_COMMIT"
  weave_validate_support_safe_evidence_url "${WEAVE_CANDIDATE_EVIDENCE_REF:-}" ||
    fail "isolated teardown requires a support-safe HTTPS WEAVE_CANDIDATE_EVIDENCE_REF without credentials, query, or fragment"
  [[ "${WEAVE_E2E_OUTPUT_ROOT:-}" == /* ]] ||
    fail "isolated teardown requires an absolute WEAVE_E2E_OUTPUT_ROOT"
  [[ -n "${WEAVE_TEARDOWN_OWNERSHIP_FILE:-}" ]] ||
    fail "isolated teardown requires WEAVE_TEARDOWN_OWNERSHIP_FILE"

  local expected_file="${WEAVE_E2E_OUTPUT_ROOT%/}/${TF_VAR_isolated_e2e_namespace}/teardown-ownership.json"
  [[ "${WEAVE_TEARDOWN_OWNERSHIP_FILE}" == "${expected_file}" ]] ||
    fail "teardown ownership evidence is outside the exact run-owned path"
  [[ -f "${WEAVE_TEARDOWN_OWNERSHIP_FILE}" ]] ||
    fail "teardown ownership evidence is missing"
  [[ "$(file_mode "${WEAVE_TEARDOWN_OWNERSHIP_FILE}")" == "600" ]] ||
    fail "teardown ownership evidence must be mode 0600"

  command -v jq >/dev/null 2>&1 || fail "jq is required for isolated teardown ownership validation"
  jq -e \
    --arg namespace "${TF_VAR_isolated_e2e_namespace}" \
    --arg runId "${WEAVE_E2E_RUN_ID}" \
    --arg candidateCommit "${WEAVE_CANDIDATE_COMMIT}" \
    --arg candidateEvidenceRef "${WEAVE_CANDIDATE_EVIDENCE_REF}" \
    '.schemaVersion == "weave.isolated-e2e-teardown-ownership.v1" and
     .scope == "isolated" and
     .namespace == $namespace and
     .runId == $runId and
     .candidateCommit == $candidateCommit and
     .candidateEvidenceRef == $candidateEvidenceRef and
     .resourcePrefix == $namespace' \
    "${WEAVE_TEARDOWN_OWNERSHIP_FILE}" >/dev/null ||
    fail "teardown ownership evidence does not match the exact run and candidate"

  [[ "${WEAVE_REMOVE_VOLUMES:-false}" == "true" ]] ||
    fail "isolated teardown must remove its run-owned volumes"
}

bootstrap_env_file() {
  printf '%s/bootstrap.env' "$(weave_workspace_generated_dir "${ROOT_DIR}")"
}

load_bootstrap_env() {
  local env_file
  env_file="$(bootstrap_env_file)"

  if [[ -f "${env_file}" ]]; then
    # shellcheck disable=SC1090
    source "${env_file}"
  fi
}

container_names() {
  printf '%s\n' \
    "$(weave_container_name proxy)" \
    "$(weave_container_name keycloak)" \
    "$(weave_container_name backend)" \
    "$(weave_container_name mas)" \
    "$(weave_container_name synapse)" \
    "$(weave_container_name nextcloud)" \
    "$(weave_container_name db)" \
    "$(weave_container_name mcp-server)" \
    "$(weave_container_name mailpit)"
}

volume_names() {
  printf '%s\n' \
    "$(weave_volume_name caddy_data)" \
    "$(weave_volume_name caddy_config)" \
    "$(weave_volume_name db_data)" \
    "$(weave_volume_name keycloak_data)" \
    "$(weave_volume_name mailpit_data)" \
    "$(weave_volume_name nextcloud_data)" \
    "$(weave_volume_name synapse_data)" \
    "$(weave_volume_name matrix_chat_appservice_runtime)"
}

docker_resource_exists() {
  local kind="$1"
  local name="$2"
  docker "${kind}" inspect "${name}" >/dev/null 2>&1
}

resource_ownership_labels() {
  local kind="$1"
  local name="$2"
  case "${kind}" in
    container)
      docker container inspect --format '{{ index .Config.Labels "com.massimotter.weave.scope" }}|{{ index .Config.Labels "com.massimotter.weave.namespace" }}' "${name}"
      ;;
    volume|network)
      docker "${kind}" inspect --format '{{ index .Labels "com.massimotter.weave.scope" }}|{{ index .Labels "com.massimotter.weave.namespace" }}' "${name}"
      ;;
    *) fail "unsupported Docker resource kind ${kind}" ;;
  esac
}

assert_isolated_resource_owned() {
  local kind="$1"
  local name="$2"
  isolated_intent_detected || return 0

  local labels
  labels="$(resource_ownership_labels "${kind}" "${name}" 2>/dev/null || true)"
  [[ "${labels}" == "isolated|${TF_VAR_isolated_e2e_namespace}" ]] ||
    fail "refusing to remove ${kind} ${name}: ownership labels do not match the isolated run"
}

preflight_isolated_resource_ownership() {
  isolated_intent_detected || return 0
  dry_run_enabled && return 0

  local name
  while IFS= read -r name; do
    if docker_resource_exists container "${name}"; then
      assert_isolated_resource_owned container "${name}"
    fi
  done < <(container_names)

  while IFS= read -r name; do
    if docker_resource_exists volume "${name}"; then
      assert_isolated_resource_owned volume "${name}"
    fi
  done < <(volume_names)

  name="$(weave_network_name)"
  if docker_resource_exists network "${name}"; then
    assert_isolated_resource_owned network "${name}"
  fi
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

  if weave_isolated_e2e_enabled && [[ ! -f "$(weave_iac_state_file "${dir}")" ]]; then
    return
  fi

  local attempt
  for ((attempt = 1; attempt <= IAC_DESTROY_MAX_ATTEMPTS; attempt++)); do
    if weave_iac_init "${dir}" -input=false >/dev/null 2>&1 &&
      weave_iac "${dir}" destroy -refresh=false -input=false -auto-approve; then
      if ((attempt > 1)); then
        log "OpenTofu destroy recovered for ${dir} on attempt ${attempt}/${IAC_DESTROY_MAX_ATTEMPTS}."
      fi
      return
    fi

    if ((attempt < IAC_DESTROY_MAX_ATTEMPTS)); then
      log "OpenTofu destroy attempt ${attempt}/${IAC_DESTROY_MAX_ATTEMPTS} did not complete for ${dir}; retrying the idempotent destroy."
      sleep "${IAC_DESTROY_RETRY_DELAY_SECONDS}"
    fi
  done

  IAC_DESTROY_FAILED=true
  log "OpenTofu destroy did not complete for ${dir} after ${IAC_DESTROY_MAX_ATTEMPTS} attempts; exact-name owned-resource cleanup will continue and state will be retained for diagnosis."
}

remove_container() {
  local name="$1"

  if dry_run_enabled; then
    log "DRY RUN: would remove container ${name}"
    return
  fi

  if docker_resource_exists container "${name}"; then
    assert_isolated_resource_owned container "${name}"
    log "Removing container ${name}"
    docker rm -f -v "${name}" >/dev/null
  fi
}

remove_volume() {
  local name="$1"

  if dry_run_enabled; then
    log "DRY RUN: would remove volume ${name}"
    return
  fi

  if docker_resource_exists volume "${name}"; then
    assert_isolated_resource_owned volume "${name}"
    log "Removing volume ${name}"
    docker volume rm -f "${name}" >/dev/null
  fi
}

remove_network() {
  local name
  name="$(weave_network_name)"

  if dry_run_enabled; then
    log "DRY RUN: would remove network ${name}"
    return
  fi

  if docker_resource_exists network "${name}"; then
    assert_isolated_resource_owned network "${name}"
    log "Removing network ${name}"
    docker network rm "${name}" >/dev/null
  fi
}

forget_matrix_volume_terraform_state() {
  if dry_run_enabled; then
    log "DRY RUN: would remove stale OpenTofu state for $(weave_volume_name synapse_data), its permission provisioner, and $(weave_volume_name matrix_chat_appservice_runtime)"
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
  [[ -n "${WEAVE_TEARDOWN_EVIDENCE_FILE:-}" ]]
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
  [[ "${WEAVE_CANDIDATE_COMMIT}" =~ ^[0-9a-f]{40}$ ]] ||
    fail "WEAVE_CANDIDATE_COMMIT must be a full lowercase SHA-1 commit identifier."
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
  while IFS= read -r container_name; do
    if ! docker container inspect "${container_name}" >/dev/null 2>&1; then
      continue
    fi
    attached_networks="$(docker inspect --format '{{range $name, $_ := .NetworkSettings.Networks}}{{$name}}{{"\n"}}{{end}}' "${container_name}")"
    printf '%s\n' "${attached_networks}" | grep -Fxq "${TF_VAR_docker_network_name}" ||
      fail "Refusing isolated teardown evidence: ${container_name} is not attached to the namespace-owned network."
  done < <(container_names)
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

  while IFS= read -r resource_name; do
    count="$(resource_count container "${resource_name}")"
    total=$((total + count))
    containers_json="$(jq --arg name "${resource_name}" --argjson count "${count}" '. + {($name): $count}' <<<"${containers_json}")"
  done < <(container_names)

  count="$(resource_count network "${TF_VAR_docker_network_name}")"
  total=$((total + count))
  networks_json="$(jq --arg name "${TF_VAR_docker_network_name}" --argjson count "${count}" '. + {($name): $count}' <<<"${networks_json}")"

  while IFS= read -r resource_name; do
    count="$(resource_count volume "${resource_name}")"
    total=$((total + count))
    volumes_json="$(jq --arg name "${resource_name}" --argjson count "${count}" '. + {($name): $count}' <<<"${volumes_json}")"
  done < <(volume_names)

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

required_destructive_confirmation() {
  printf '%s' "${TF_VAR_tenant_slug:-weave}"
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
  while IFS= read -r volume; do
    printf '  - %s\n' "${volume}" >&2
  done < <(volume_names)

  cat >&2 <<EOF

Generated local secrets/config in .generated/ are not removed by this helper;
back them up separately before deleting them manually.

Required confirmation:
  WEAVE_REMOVE_VOLUMES=true
  WEAVE_CONFIRM_DESTRUCTIVE_RESET=${required_confirmation}
EOF
}

confirm_persistent_volume_removal() {
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

remove_isolated_runtime_assets() {
  weave_isolated_e2e_enabled || return 0
  [[ "${IAC_DESTROY_FAILED}" == "false" ]] || return 0

  local workspace_generated infra_generated runtime_root
  workspace_generated="$(weave_workspace_generated_dir "${ROOT_DIR}")"
  infra_generated="$(weave_infra_generated_dir "${ROOT_DIR}")"
  runtime_root="$(weave_isolated_run_root)/runtime"

  [[ "${workspace_generated}" == "${ROOT_DIR}/.generated/isolated/${TF_VAR_isolated_e2e_namespace}" ]] ||
    fail "refusing to remove an unexpected workspace generated path"
  [[ "${infra_generated}" == "${INFRA_DIR}/.generated/isolated/${TF_VAR_isolated_e2e_namespace}" ]] ||
    fail "refusing to remove an unexpected infrastructure generated path"
  [[ "${runtime_root}" == "${WEAVE_E2E_OUTPUT_ROOT%/}/${TF_VAR_isolated_e2e_namespace}/runtime" ]] ||
    fail "refusing to remove an unexpected isolated runtime path"

  rm -rf -- "${workspace_generated}" "${infra_generated}" "${runtime_root}"
}

require_runtime_commands() {
  if dry_run_enabled; then
    return
  fi

  command -v docker >/dev/null 2>&1 || fail "Missing required command: docker"
  command -v "${WEAVE_IAC_BIN}" >/dev/null 2>&1 ||
    fail "Missing required command: ${WEAVE_IAC_BIN} (OpenTofu/tofu by default)"
}

main() {
  if isolated_intent_detected; then
    # An isolated caller must supply its exact run environment. Never fall back
    # to the persistent bootstrap path: a stale file there could otherwise
    # redirect teardown toward resources owned by another run.
    require_isolated_ownership
  else
    load_bootstrap_env
    isolated_intent_detected &&
      fail "persistent bootstrap env contains stale isolated-E2E markers"
  fi
  require_runtime_commands
  verify_isolated_teardown_scope
  trap on_teardown_exit EXIT
  trap 'exit 130' INT
  trap 'exit 143' TERM
  preflight_isolated_resource_ownership

  if weave_isolated_e2e_enabled || [[ "${WEAVE_IAC_DESTROY:-false}" == "true" ]]; then
    iac_destroy "${KEYCLOAK_DIR}"
    iac_destroy "${INFRA_DIR}"
  fi

  local name
  while IFS= read -r name; do
    remove_container "${name}"
  done < <(container_names)

  remove_network

  if weave_isolated_e2e_enabled; then
    while IFS= read -r name; do
      remove_volume "${name}"
    done < <(volume_names)
    if [[ "${IAC_DESTROY_FAILED}" == "false" ]]; then
      forget_matrix_volume_terraform_state
    fi
    remove_chat_e2e_proof_credential
    write_isolated_teardown_evidence
    remove_isolated_runtime_assets
  elif confirm_persistent_volume_removal; then
    while IFS= read -r name; do
      remove_volume "${name}"
    done < <(volume_names)
    forget_matrix_volume_terraform_state
  fi

  trap - EXIT INT TERM
  if [[ "${IAC_DESTROY_FAILED}" == "true" ]]; then
    fail "OpenTofu destroy failed; run-owned state was retained for diagnosis"
  fi
}

main "$@"
