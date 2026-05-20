#!/usr/bin/env bash
# shellcheck shell=bash
# shellcheck disable=SC2016

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_DIR="$(cd -- "${ROOT_DIR}/.." && pwd)"

fail() {
  printf '%s\n' "$*" >&2
  exit 1
}

assert_file_contains() {
  local file="$1"
  local needle="$2"
  grep -Fq -- "${needle}" "${file}" || fail "Expected ${file} to contain: ${needle}"
}

assert_file_absent() {
  local file="$1"
  local needle="$2"
  ! grep -Fq -- "${needle}" "${file}" || fail "Did not expect ${file} to contain: ${needle}"
}

assert_file_contains_once() {
  local file="$1"
  local needle="$2"
  local count
  count="$(grep -F -c -- "${needle}" "${file}" || true)"
  [[ "${count}" == "1" ]] || fail "Expected ${file} to contain exactly once: ${needle} (found ${count})"
}

backend_main="${ROOT_DIR}/01-infrastructure/modules/backend/main.tf"
infra_main="${ROOT_DIR}/01-infrastructure/main.tf"
infra_outputs="${ROOT_DIR}/01-infrastructure/outputs.tf"
install_script="${ROOT_DIR}/install.sh"
keycloak_main="${ROOT_DIR}/02-keycloak-setup/modules/tenant-identity/main.tf"
release_env="${ROOT_DIR}/release.env.example"
release_verify="${ROOT_DIR}/release-verify.sh"
admin_doc="${REPO_DIR}/docs/admin-user-activation.md"
caldav_doc="${REPO_DIR}/docs/calendar-caldav-external-clients.md"
connector_doc="${REPO_DIR}/docs/connector-runtime-guardrails.md"
matrix_workspace_doc="${REPO_DIR}/docs/matrix-default-workspace.md"
matrix_e2ee_doc="${REPO_DIR}/docs/matrix-e2ee-posture.md"
openproject_doc="${REPO_DIR}/docs/openproject-boards-runtime.md"
openproject_compose="${ROOT_DIR}/docker-compose.openproject.yml"
caddy_template="${ROOT_DIR}/01-infrastructure/templates/Caddyfile.tpl"
support_bundle="${ROOT_DIR}/support-bundle.sh"

for file in "${backend_main}" "${infra_main}" "${infra_outputs}" "${install_script}" "${release_verify}" "${keycloak_main}" "${release_env}" "${admin_doc}" "${caldav_doc}" "${connector_doc}" "${matrix_workspace_doc}" "${matrix_e2ee_doc}" "${openproject_doc}" "${openproject_compose}" "${support_bundle}" "${caddy_template}"; do
  [[ -f "${file}" ]] || fail "Missing expected contract file: ${file}"
done

# CalDAV external-client metadata must stay public/no-secret and fail-closed.
assert_file_contains "${backend_main}" 'WEAVE_CALDAV_EXTERNAL_DISCOVERY_URL=${var.caldav_external_discovery_url}'
assert_file_contains "${backend_main}" 'WEAVE_CALDAV_EXTERNAL_CREDENTIAL_MODE=${var.caldav_external_credential_mode}'
assert_file_contains "${backend_main}" 'WEAVE_CALDAV_EXTERNAL_PROFILE_PASSWORD_MODE=${var.caldav_external_profile_password_mode}'
assert_file_contains "${backend_main}" 'WEAVE_CALDAV_EXTERNAL_PRIVATE_USER_CALENDARS=${var.caldav_external_private_user_calendars}'
assert_file_contains "${infra_main}" 'caldav_external_discovery_url'
assert_file_contains "${infra_main}" 'nextcloud-login-flow-app-password'
assert_file_contains "${infra_main}" 'caldav_external_profile_password_mode            = "omit"'
assert_file_contains "${infra_main}" 'caldav_external_private_user_calendars           = "disabled"'
assert_file_contains "${install_script}" 'WEAVE_CALDAV_EXTERNAL_DISCOVERY_URL'
assert_file_contains "${install_script}" 'WEAVE_CALDAV_EXTERNAL_PROFILE_PASSWORD_MODE'
assert_file_contains "${release_env}" 'WEAVE_CALDAV_EXTERNAL_DISCOVERY_URL=https://files.weave.example/remote.php/dav'
assert_file_absent "${caldav_doc}" 'WEAVE_CALDAV_BACKEND_TOKEN='
assert_file_absent "${release_env}" 'WEAVE_CALDAV_BACKEND_TOKEN='
assert_file_contains "${install_script}" 'printf '\''export WEAVE_CHAT_E2EE=%q\n'\'' "active-architecture-gated"'
assert_file_contains "${infra_outputs}" 'WEAVE_CHAT_E2EE                              = "active-architecture-gated"'
assert_file_contains "${ROOT_DIR}/operator-check.sh" 'features.chatE2ee == false'
assert_file_contains "${ROOT_DIR}/operator-check.sh" 'm.room.encryption'
assert_file_contains "${ROOT_DIR}/operator-check.sh" 'room_keys/version'
assert_file_contains "${ROOT_DIR}/smoke-test.sh" 'features.chatE2ee == false'
assert_file_contains "${ROOT_DIR}/smoke-test.sh" 'curl_auth_status()'
assert_file_contains "${ROOT_DIR}/smoke-test.sh" 'm.room.encryption'
assert_file_contains "${ROOT_DIR}/smoke-test.sh" 'room_keys/version'
assert_file_contains "${ROOT_DIR}/smoke-test.sh" 'diagnostic only and does not prove global E2EE recovery readiness'
assert_file_contains "${support_bundle}" 'WEAVE_CHAT_E2EE'
assert_file_contains "${matrix_workspace_doc}" 'Matrix E2EE is active architecture scope but not complete.'
assert_file_contains "${matrix_e2ee_doc}" 'Bot, assistant, and connector participation in encrypted rooms remains fail-closed'
assert_file_contains "${matrix_e2ee_doc}" 'Matrix message bodies are not backend/support-readable'
assert_file_contains "${backend_main}" 'WEAVE_WORKSPACE_CALENDAR_ENABLED=true'
assert_file_contains "${backend_main}" 'WEAVE_WORKSPACE_CALENDAR_READINESS=ready'
assert_file_contains "${backend_main}" 'WEAVE_WORKSPACE_BOARDS_ENABLED=true'
assert_file_contains "${backend_main}" 'WEAVE_WORKSPACE_BOARDS_READINESS=ready'
assert_file_absent "${backend_main}" 'WEAVE_BOARDS_PREVIEW_RUNTIME_ENABLED=true'
assert_file_contains "${backend_main}" 'WEAVE_BOARDS_PREVIEW_RUNTIME_ENABLED=${var.boards_preview_runtime_enabled}'
assert_file_contains_once "${backend_main}" 'WEAVE_BOARDS_PREVIEW_RUNTIME_ENABLED='
assert_file_contains "${infra_main}" 'boards_preview_runtime_enabled                   = var.boards_preview_runtime_enabled'
assert_file_contains "${install_script}" 'weave-team-engineering'
assert_file_contains "${install_script}" 'weave-channel-engineering-general'
assert_file_contains "${ROOT_DIR}/smoke-test.sh" 'weave-team-engineering'
assert_file_contains "${ROOT_DIR}/operator-check.sh" 'weave-channel-engineering-general'
assert_file_contains "${release_verify}" 'weave-channel-engineering-general'
legacy_e2ee_marker='planned-not-'
legacy_e2ee_marker+='enabled'
assert_file_absent "${install_script}" "${legacy_e2ee_marker}"
assert_file_absent "${infra_outputs}" "${legacy_e2ee_marker}"

# Keycloak must declare product roles/groups, and guest must remain distinct from member/admin.
for role in owner admin member guest; do
  grep -Eq "^[[:space:]]+${role}[[:space:]]+=" "${keycloak_main}" || fail "Expected Keycloak product role/group entry for: ${role}"
done
assert_file_contains "${keycloak_main}" 'workspace-guests'
assert_file_contains "${keycloak_main}" 'keycloak_group_roles'
assert_file_contains "${keycloak_main}" 'keycloak_user_roles'
assert_file_contains "${admin_doc}" 'Guests are mapped to `workspace-guests`, not member/admin groups.'

# Connector/interop runtime guardrails must default closed and keep public provider callbacks blocked.
assert_file_contains "${backend_main}" 'WEAVE_INTEROP_ENABLED=${var.interop_enabled}'
assert_file_contains "${backend_main}" 'WEAVE_INTEROP_SLACK_ENABLED=${var.interop_slack_enabled}'
assert_file_contains "${backend_main}" 'WEAVE_INTEROP_TEAMS_ENABLED=${var.interop_teams_enabled}'
assert_file_contains "${backend_main}" 'WEAVE_CONNECTORS_PUBLIC_SDK_ENABLED=${var.connectors_public_sdk_enabled}'
assert_file_contains "${backend_main}" 'WEAVE_BOARDS_PREVIEW_RUNTIME_ENABLED=${var.boards_preview_runtime_enabled}'
assert_file_contains "${infra_main}" 'connector_provider_callbacks_exposed ? ""'
assert_file_contains "${infra_main}" 'interop_enabled                                  = false'
assert_file_contains "${infra_main}" 'interop_slack_enabled                            = false'
assert_file_contains "${infra_main}" 'connectors_public_sdk_enabled                    = false'
assert_file_contains "${infra_main}" 'boards_preview_runtime_enabled                   = var.boards_preview_runtime_enabled'
assert_file_contains "${install_script}" 'weave-team-engineering'
assert_file_contains "${install_script}" 'weave-channel-engineering-general'
assert_file_contains "${ROOT_DIR}/smoke-test.sh" 'weave-team-engineering'
assert_file_contains "${ROOT_DIR}/operator-check.sh" 'weave-channel-engineering-general'
assert_file_contains "${release_verify}" 'weave-channel-engineering-general'
assert_file_contains "${ROOT_DIR}/01-infrastructure/variables.tf" 'variable "boards_preview_runtime_enabled"'
assert_file_contains "${ROOT_DIR}/01-infrastructure/variables.tf" 'Defaults false; expensive live feature-proof runs may set true'
assert_file_contains "${caddy_template}" 'connector_provider_callbacks_guard'
assert_file_contains "${connector_doc}" 'WEAVE_BOARDS_PREVIEW_RUNTIME_ENABLED=false'
assert_file_contains "${connector_doc}" 'provider callback routes such as Slack OAuth and event ingestion are blocked at Caddy with `404`'
assert_file_contains "${connector_doc}" 'do not commit demo OAuth secrets, webhook signing secrets, bot tokens, access tokens, or refresh tokens'

# OpenProject is the first real Boards provider path, but must stay optional, read-only, and secret-safe.
assert_file_contains "${backend_main}" 'WEAVE_BOARDS_PREVIEW_PROVIDER=${var.boards_preview_provider}'
assert_file_contains "${backend_main}" 'WEAVE_BOARDS_OPENPROJECT_RUNTIME_ENABLED=${var.boards_openproject_runtime_enabled}'
assert_file_contains "${backend_main}" 'WEAVE_BOARDS_OPENPROJECT_READ_SYNC_ENABLED=${var.boards_openproject_read_sync_enabled}'
assert_file_contains "${backend_main}" 'WEAVE_BOARDS_OPENPROJECT_PROVIDER_WRITES_ENABLED=${var.boards_openproject_provider_writes_enabled}'
assert_file_contains "${backend_main}" 'WEAVE_BOARDS_OPENPROJECT_AUTH_MODE=${var.boards_openproject_auth_mode}'
assert_file_contains "${backend_main}" 'WEAVE_BOARDS_OPENPROJECT_BASE_URL=${var.boards_openproject_base_url}'
assert_file_contains "${backend_main}" 'WEAVE_BOARDS_OPENPROJECT_API_TOKEN=${var.boards_openproject_api_token}'
assert_file_contains "${infra_main}" 'boards_preview_provider'
assert_file_contains "${infra_main}" 'boards_openproject_provider_writes_enabled       = var.boards_openproject_provider_writes_enabled'
assert_file_contains "${install_script}" 'TF_VAR_boards_openproject_provider_writes_enabled=false'
assert_file_contains "${release_env}" 'TF_VAR_boards_openproject_provider_writes_enabled=false'
assert_file_contains "${openproject_compose}" 'profiles:'
assert_file_contains "${openproject_compose}" 'weave-openproject'
assert_file_contains "${openproject_compose}" 'TF_VAR_openproject_secret_key_base'
assert_file_contains "${openproject_doc}" 'OpenProject is a backend/provider engine only'
assert_file_contains "${openproject_doc}" 'TF_VAR_boards_openproject_context_authorization_enabled=true'
assert_file_contains "${openproject_doc}" 'TF_VAR_boards_openproject_provider_writes_enabled=false'
assert_file_contains "${openproject_doc}" 'The backend-held API token is never written to `app-config.env`'
assert_file_contains "${ROOT_DIR}/operator-check.sh" 'OpenProject read-sync requires Context/Space authorization gate enabled'
assert_file_absent "${release_env}" 'TF_VAR_boards_openproject_api_token=replace-me'
assert_file_absent "${install_script}" 'WEAVE_BOARDS_OPENPROJECT_API_TOKEN=%q'
assert_file_contains "${connector_doc}" 'Boards provider secrets follow the same rule'

printf '%s\n' 'infra product contract tests passed'
