#!/usr/bin/env bash
# shellcheck shell=bash
# shellcheck disable=SC2016

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
infra_main="${ROOT_DIR}/01-infrastructure/main.tf"
infra_variables="${ROOT_DIR}/01-infrastructure/variables.tf"
backend_main="${ROOT_DIR}/01-infrastructure/modules/backend/main.tf"
backend_variables="${ROOT_DIR}/01-infrastructure/modules/backend/variables.tf"
matrix_main="${ROOT_DIR}/01-infrastructure/modules/matrix/main.tf"
matrix_variables="${ROOT_DIR}/01-infrastructure/modules/matrix/variables.tf"
matrix_outputs="${ROOT_DIR}/01-infrastructure/modules/matrix/outputs.tf"
homeserver_template="${ROOT_DIR}/01-infrastructure/templates/homeserver.yaml.tpl"
registration_template="${ROOT_DIR}/01-infrastructure/templates/synapse-appservice.yaml.tpl"
caddy_template="${ROOT_DIR}/01-infrastructure/templates/Caddyfile.tpl"
install_script="${ROOT_DIR}/install.sh"
backup_script="${ROOT_DIR}/backup.sh"
restore_script="${ROOT_DIR}/restore-smoke.sh"
operator_check="${ROOT_DIR}/operator-check.sh"
support_bundle="${ROOT_DIR}/support-bundle.sh"
teardown_script="${ROOT_DIR}/teardown.sh"
identity_script="${ROOT_DIR}/isolated-e2e-identities.sh"
provider_proof_script="${ROOT_DIR}/isolated-e2e-chat-provider-proof.sh"
release_env="${ROOT_DIR}/release.env.example"
operator_doc="${ROOT_DIR}/../docs/matrix-synapse-chat-appservice.md"

fail() {
  printf '%s\n' "$*" >&2
  exit 1
}

assert_contains() {
  local file="$1"
  local value="$2"
  grep -Fq -- "${value}" "${file}" || fail "Expected ${file} to contain: ${value}"
}

assert_absent() {
  local file="$1"
  local value="$2"
  ! grep -Fq -- "${value}" "${file}" || fail "Did not expect ${file} to contain: ${value}"
}

for required_file in \
  "${infra_main}" "${infra_variables}" "${backend_main}" "${backend_variables}" \
  "${matrix_main}" "${matrix_variables}" "${matrix_outputs}" \
  "${homeserver_template}" "${registration_template}" "${caddy_template}" \
  "${install_script}" "${backup_script}" "${restore_script}" "${operator_check}" \
  "${support_bundle}" "${teardown_script}" "${release_env}" "${operator_doc}"; do
  [[ -f "${required_file}" ]] || fail "Missing Matrix Chat Application Service contract file: ${required_file}"
done
[[ -f "${identity_script}" && -f "${provider_proof_script}" ]] || fail "Missing isolated Chat provider proof lifecycle scripts"

# Registration is callback-capable, rate-limited, and constrained to exact
# opaque Weave virtual-user/alias namespaces. There is no room or broad wildcard
# namespace and no direct human/Keycloak identity projection.
assert_contains "${registration_template}" 'url: "${appservice_callback_url}"'
assert_contains "${registration_template}" 'as_token: "${appservice_as_token}"'
assert_contains "${registration_template}" 'hs_token: "${appservice_hs_token}"'
assert_contains "${registration_template}" 'rate_limited: true'
assert_contains "${registration_template}" 'receive_ephemeral: false'
assert_contains "${registration_template}" "regex: '^@\${virtual_user_prefix}[a-z0-9]{26,64}:\${matrix_homeserver_regex}$'"
assert_contains "${registration_template}" "regex: '^#\${virtual_user_prefix}[a-z0-9]{26,64}:\${matrix_homeserver_regex}$'"
assert_absent "${registration_template}" 'regex: "'
assert_contains "${registration_template}" 'exclusive: true'
assert_contains "${registration_template}" 'rooms: []'
assert_absent "${registration_template}" '.*'
assert_absent "${registration_template}" 'rate_limited: false'
assert_absent "${registration_template}" 'exclusive: false'

assert_contains "${infra_main}" 'id                  = "weave-chat-synapse"'
assert_contains "${infra_main}" 'sender_localpart    = "_weave_appservice"'
assert_contains "${infra_main}" 'virtual_user_prefix = "_weave_"'
assert_contains "${infra_main}" '/api/internal/chat/matrix/appservice'
assert_contains "${infra_main}" 'matrix_homeserver_regex     = replace(local.public_hosts.matrix, ".", "\\.")'
assert_contains "${infra_main}" 'matrix_chat_appservice_registration_contract = yamldecode(templatefile('
assert_contains "${infra_main}" 'Matrix Chat Application Service registration must remain valid YAML with exact Weave virtual-user and alias namespaces.'
assert_contains "${homeserver_template}" 'app_service_config_files:'
assert_contains "${homeserver_template}" '${matrix_chat_appservice_registration_path}'

# Token inputs are independent sensitive values. The guard fails closed on
# equality/reuse without printing either value.
assert_contains "${infra_variables}" 'variable "matrix_chat_appservice_as_token"'
assert_contains "${infra_variables}" 'variable "matrix_chat_appservice_hs_token"'
[[ "$(grep -A5 'variable "matrix_chat_appservice_as_token"' "${infra_variables}" | grep -c 'sensitive   = true')" == "1" ]] || fail "as_token Terraform input is not sensitive"
[[ "$(grep -A5 'variable "matrix_chat_appservice_hs_token"' "${infra_variables}" | grep -c 'sensitive   = true')" == "1" ]] || fail "hs_token Terraform input is not sensitive"
assert_contains "${infra_main}" 'resource "terraform_data" "matrix_chat_appservice_secret_guard"'
assert_contains "${infra_main}" 'var.matrix_chat_appservice_as_token != var.matrix_chat_appservice_hs_token'
assert_contains "${install_script}" 'set_default_secret TF_VAR_matrix_chat_appservice_as_token "$(random_hex 32)"'
assert_contains "${install_script}" 'set_default_secret TF_VAR_matrix_chat_appservice_hs_token "$(random_hex 32)"'
assert_contains "${install_script}" 'TF_VAR_matrix_chat_appservice_as_token'
assert_contains "${install_script}" 'TF_VAR_matrix_chat_appservice_hs_token'

# The runtime volume is populated privately and mounted read-only into exactly
# the backend and Synapse. Backend configuration contains file paths, never raw
# token-valued environment variables.
assert_contains "${matrix_main}" 'resource "docker_volume" "appservice_runtime"'
assert_contains "${matrix_main}" 'APPSERVICE_REGISTRATION_SOURCE'
assert_contains "${matrix_main}" '! cmp -s /target/as-token /target/hs-token'
assert_contains "${matrix_main}" 'container_path = var.appservice_runtime_container_path'
assert_contains "${matrix_main}" 'read_only      = true'
assert_contains "${matrix_outputs}" 'output "appservice_runtime_volume_name"'
assert_contains "${backend_main}" 'WEAVE_CHAT_PROVIDER=${var.chat_provider}'
assert_contains "${backend_main}" 'WEAVE_CHAT_STORAGE_MODE=${var.chat_storage_mode}'
assert_contains "${backend_main}" 'WEAVE_CHAT_MATRIX_INTERNAL_BASE_URL=${var.matrix_internal_base_url}'
assert_contains "${backend_main}" 'WEAVE_CHAT_MATRIX_APPSERVICE_AS_TOKEN_FILE=${var.matrix_appservice_as_token_file}'
assert_contains "${backend_main}" 'WEAVE_CHAT_MATRIX_APPSERVICE_HS_TOKEN_FILE=${var.matrix_appservice_hs_token_file}'
assert_contains "${backend_main}" 'volume_name    = var.matrix_appservice_runtime_volume_name'
assert_contains "${backend_main}" 'read_only      = true'
assert_absent "${backend_main}" 'WEAVE_CHAT_MATRIX_APPSERVICE_AS_TOKEN='
assert_absent "${backend_main}" 'WEAVE_CHAT_MATRIX_APPSERVICE_HS_TOKEN='
assert_contains "${infra_main}" 'matrix_internal_base_url                         = "http://${local.service_names.synapse}:8008"'
assert_contains "${infra_main}" 'matrix_appservice_as_token_file                  = "${local.matrix_chat_appservice.runtime_path}/as-token"'
assert_contains "${infra_main}" 'matrix_appservice_hs_token_file                  = "${local.matrix_chat_appservice.runtime_path}/hs-token"'

# The E2E provider proof has a distinct, isolated-only, run-bound security
# plane. Its value is generated into a private host file and therefore never
# enters OpenTofu input/state or reuses either Application Service token.
assert_contains "${infra_variables}" 'variable "chat_e2e_proof_enabled"'
assert_contains "${infra_variables}" 'variable "chat_e2e_proof_token_host_path"'
assert_contains "${infra_variables}" 'variable "chat_e2e_proof_run_id"'
assert_contains "${infra_main}" '!var.chat_e2e_proof_enabled || ('
assert_contains "${infra_main}" 'var.isolated_e2e_enabled &&'
assert_contains "${infra_main}" 'fileexists(var.chat_e2e_proof_token_host_path)'
assert_contains "${infra_main}" 'var.chat_e2e_proof_run_id == "" &&'
assert_contains "${backend_main}" 'WEAVE_CHAT_E2E_PROOF_ENABLED=false'
assert_contains "${backend_main}" 'WEAVE_E2E_STACK_SCOPE=isolated'
assert_contains "${backend_main}" 'WEAVE_CHAT_E2E_PROOF_ENABLED=true'
assert_contains "${backend_main}" 'WEAVE_CHAT_E2E_PROOF_TOKEN_FILE=${var.chat_e2e_proof_token_container_path}'
assert_contains "${backend_main}" 'WEAVE_CHAT_E2E_PROOF_RUN_ID=${var.chat_e2e_proof_run_id}'
assert_contains "${backend_main}" 'host_path      = volumes.value'
assert_contains "${backend_main}" 'container_path = var.chat_e2e_proof_token_container_path'
assert_absent "${matrix_main}" 'chat_e2e_proof'
assert_contains "${identity_script}" 'random_proof_token >"${CHAT_PROOF_TOKEN_PATH}"'
assert_contains "${identity_script}" 'TF_VAR_chat_e2e_proof_token_host_path'
assert_contains "${install_script}" '${proof_token}" != "${TF_VAR_matrix_chat_appservice_as_token}"'
assert_contains "${install_script}" '${proof_token}" != "${TF_VAR_matrix_chat_appservice_hs_token}"'
assert_contains "${provider_proof_script}" '/api/internal/e2e/chat/provider-proof'
assert_absent "${provider_proof_script}" 'preproject_outsider_fixture'
assert_contains "${provider_proof_script}" 'outsiderProviderMappingAbsent:true'
assert_contains "${provider_proof_script}" '--arg runId "${RUN_ID}"'
assert_absent "${provider_proof_script}" '/api/internal/chat/matrix/appservice/evidence'

# Public routing always hides the callback, including nested transaction/query
# paths, while the private registration addresses the backend directly.
[[ "$(grep -c '@matrix_chat_appservice_callback path /api/internal/chat/matrix/appservice /api/internal/chat/matrix/appservice/\*' "${caddy_template}")" == "2" ]] || fail "Caddy must explicitly deny the callback on both public product/API sites"
assert_contains "${caddy_template}" 'handle @matrix_chat_appservice_callback {'
assert_contains "${caddy_template}" 'respond "Not Found" 404'
[[ "$(grep -c '@chat_e2e_provider_proof path /api/internal/e2e/chat/provider-proof /api/internal/e2e/chat/provider-proof/\*' "${caddy_template}")" == "2" ]] || fail "Caddy must explicitly deny the isolated proof endpoint on both public product/API sites"

# Lifecycle and diagnostics preserve private material in backups only. Restore
# checks mounts without reading values into output; support bundles expose only
# configured/mode booleans and exclude registration/token files.
assert_contains "${backup_script}" 'Synapse Application Service registration/tokens'
assert_contains "${restore_script}" 'verify_matrix_chat_appservice_runtime'
assert_contains "${restore_script}" 'matrix_chat_appservice_registration_and_secret_mounts'
assert_contains "${operator_check}" 'assert_backend_matrix_chat_provider_config'
assert_contains "${operator_check}" 'public API route exposed the private Matrix Chat Application Service callback'
assert_contains "${support_bundle}" 'WEAVE_CHAT_MATRIX_APPSERVICE_CONFIGURED'
assert_contains "${support_bundle}" 'matrix_appservice_tokens_and_registration'
assert_contains "${support_bundle}" 'chat_e2e_proof_token_and_run_binding'
assert_absent "${support_bundle}" 'TF_VAR_matrix_chat_appservice_as_token'
assert_absent "${support_bundle}" 'TF_VAR_matrix_chat_appservice_hs_token'
assert_contains "${teardown_script}" 'weave_volume_name matrix_chat_appservice_runtime'
assert_contains "${teardown_script}" 'remove_chat_e2e_proof_credential'
assert_contains "${backup_script}" "--exclude='*/chat-provider-proof.token'"
assert_contains "${backup_script}" 'Backups are disabled for disposable Chat E2E proof namespaces'
assert_contains "${release_env}" 'TF_VAR_matrix_chat_appservice_as_token=replace-with-independent-random-hex'
assert_contains "${release_env}" 'TF_VAR_matrix_chat_appservice_hs_token=replace-with-different-independent-random-hex'
assert_contains "${operator_doc}" 'Keycloak remains the human identity authority.'
assert_contains "${operator_doc}" 'docker stop weave-synapse'
assert_contains "${operator_doc}" 'WEAVE_CHAT_E2E_PROOF_ENABLED=false'

printf 'Matrix Chat Application Service contract tests passed\n'
