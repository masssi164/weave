#!/usr/bin/env bash
# shellcheck shell=bash
# shellcheck disable=SC2016

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_DIR="$(cd -- "${ROOT_DIR}/.." && pwd)"
MONOREPO_DIR="$(cd -- "${REPO_DIR}/.." && pwd)"

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
provider_stack_compose="${ROOT_DIR}/docker-compose.provider-stack.yml"
provider_stack_check="${ROOT_DIR}/provider-stack-fail-closed-check.sh"
openproject_live_e2e="${ROOT_DIR}/openproject-boards-live-e2e.sh"
caddy_template="${ROOT_DIR}/01-infrastructure/templates/Caddyfile.tpl"
support_bundle="${ROOT_DIR}/support-bundle.sh"
local_invite_script="${ROOT_DIR}/local-invite-link.sh"
dogfood_handoff_bundle="${MONOREPO_DIR}/tools/dogfood_handoff_bundle.py"
dogfood_ios_smoke="${MONOREPO_DIR}/tools/dogfood_ios_deeplink_smoke.sh"
dogfood_cert_smoke="${MONOREPO_DIR}/tools/dogfood_cert_persistence_smoke.py"

for file in "${backend_main}" "${infra_main}" "${infra_outputs}" "${install_script}" "${release_verify}" "${keycloak_main}" "${release_env}" "${admin_doc}" "${caldav_doc}" "${connector_doc}" "${matrix_workspace_doc}" "${matrix_e2ee_doc}" "${openproject_doc}" "${openproject_compose}" "${provider_stack_compose}" "${provider_stack_check}" "${openproject_live_e2e}" "${support_bundle}" "${caddy_template}" "${local_invite_script}" "${dogfood_handoff_bundle}" "${dogfood_ios_smoke}" "${dogfood_cert_smoke}"; do
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
assert_file_contains "${caldav_doc}" 'CalDAV/CardDAV'
assert_file_contains "${caldav_doc}" 'Forms are visible provider seams'
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
assert_file_contains "${REPO_DIR}/README.md" 'LiveKit is the active meeting/video-call provider contract; TURN/SFU hardening and recording/caption policy remain promotion gates.'
assert_file_contains "${backend_main}" 'WEAVE_WORKSPACE_CALENDAR_ENABLED=true'
assert_file_contains "${backend_main}" 'WEAVE_WORKSPACE_CALENDAR_READINESS=ready'
assert_file_contains "${backend_main}" 'WEAVE_WORKSPACE_BOARDS_ENABLED=true'
assert_file_contains "${backend_main}" 'WEAVE_WORKSPACE_BOARDS_READINESS=ready'
assert_file_absent "${backend_main}" 'WEAVE_BOARDS_RUNTIME_ENABLED=true'
assert_file_contains "${backend_main}" 'WEAVE_BOARDS_RUNTIME_ENABLED=${var.boards_runtime_enabled}'
assert_file_contains_once "${backend_main}" 'WEAVE_BOARDS_RUNTIME_ENABLED='
assert_file_contains "${infra_main}" 'boards_runtime_enabled                           = var.boards_runtime_enabled'
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
assert_file_contains "${keycloak_main}" 'weave-board-editors'
assert_file_contains "${keycloak_main}" 'live_e2e_test_user_capability_groups'
assert_file_contains "${keycloak_main}" 'keycloak_group_roles'
assert_file_contains "${keycloak_main}" 'keycloak_user_roles'
assert_file_contains "${keycloak_main}" 'keycloak_openid_group_membership_protocol_mapper" "weave_app_groups"'
assert_file_contains "${admin_doc}" 'Guests are mapped to `workspace-guests`, not member/admin groups.'

# Connector/interop runtime guardrails must default closed and keep public provider callbacks blocked.
assert_file_contains "${backend_main}" 'WEAVE_INTEROP_ENABLED=${var.interop_enabled}'
assert_file_contains "${backend_main}" 'WEAVE_INTEROP_SLACK_ENABLED=${var.interop_slack_enabled}'
assert_file_contains "${backend_main}" 'WEAVE_INTEROP_TEAMS_ENABLED=${var.interop_teams_enabled}'
assert_file_contains "${backend_main}" 'WEAVE_CONNECTORS_PUBLIC_SDK_ENABLED=${var.connectors_public_sdk_enabled}'
assert_file_contains "${backend_main}" 'WEAVE_BOARDS_RUNTIME_ENABLED=${var.boards_runtime_enabled}'
assert_file_contains "${infra_main}" 'connector_provider_callbacks_exposed ? ""'
assert_file_contains "${infra_main}" 'interop_enabled                                  = false'
assert_file_contains "${infra_main}" 'interop_slack_enabled                            = false'
assert_file_contains "${infra_main}" 'connectors_public_sdk_enabled                    = false'
assert_file_contains "${infra_main}" 'boards_runtime_enabled                           = var.boards_runtime_enabled'
assert_file_contains "${install_script}" 'weave-team-engineering'
assert_file_contains "${install_script}" 'weave-channel-engineering-general'
assert_file_contains "${ROOT_DIR}/smoke-test.sh" 'weave-team-engineering'
assert_file_contains "${ROOT_DIR}/operator-check.sh" 'weave-channel-engineering-general'
assert_file_contains "${release_verify}" 'weave-channel-engineering-general'
assert_file_contains "${ROOT_DIR}/01-infrastructure/variables.tf" 'variable "boards_runtime_enabled"'
assert_file_contains "${ROOT_DIR}/01-infrastructure/variables.tf" 'Defaults false; live feature-proof runs may set true'
assert_file_contains "${caddy_template}" 'connector_provider_callbacks_guard'
assert_file_contains "${connector_doc}" 'WEAVE_BOARDS_RUNTIME_ENABLED=false'
assert_file_contains "${connector_doc}" 'provider callback routes such as Slack OAuth and event ingestion are blocked at Caddy with `404`'
assert_file_contains "${connector_doc}" 'do not commit demo OAuth secrets, webhook signing secrets, bot tokens, access tokens, or refresh tokens'

# Provider-stack seams must be wired into the backend while optional/heavy runtimes stay fail-closed by default.
assert_file_contains "${backend_main}" 'WEAVE_PROVIDER_STACK_PROFILE=${var.provider_stack_profile}'
assert_file_contains "${backend_main}" 'WEAVE_DEVOPS_PRIMARY_PROVIDER=${var.devops_primary_provider}'
assert_file_contains "${backend_main}" 'WEAVE_DEVOPS_GITLAB_RUNTIME_ENABLED=${var.devops_gitlab_runtime_enabled}'
assert_file_contains "${backend_main}" 'WEAVE_DEVOPS_FORGEJO_RUNTIME_ENABLED=${var.devops_forgejo_runtime_enabled}'
assert_file_contains "${backend_main}" 'WEAVE_OFFICE_PRIMARY_PROVIDER=${var.office_primary_provider}'
assert_file_contains "${backend_main}" 'WEAVE_OFFICE_ONLYOFFICE_RUNTIME_ENABLED=${var.office_onlyoffice_runtime_enabled}'
assert_file_contains "${backend_main}" 'WEAVE_OFFICE_NEXTCLOUD_INTEGRATION_MODE=${var.office_nextcloud_integration_mode}'
assert_file_contains "${backend_main}" 'WEAVE_GROUPWARE_CONTACTS_RUNTIME_ENABLED=${var.groupware_contacts_runtime_enabled}'
assert_file_contains "${backend_main}" 'WEAVE_GROUPWARE_FORMS_RUNTIME_ENABLED=${var.groupware_forms_runtime_enabled}'
assert_file_contains "${infra_main}" 'devops_primary_provider                          = var.devops_primary_provider'
assert_file_contains "${infra_main}" 'office_nextcloud_integration_mode                = var.office_nextcloud_integration_mode'
assert_file_contains "${ROOT_DIR}/01-infrastructure/variables.tf" 'GitLab CE/FOSS is the default; no Premium/Ultimate dependency is allowed.'
assert_file_contains "${install_script}" 'TF_VAR_provider_stack_profile=fail-closed'
assert_file_contains "${install_script}" 'TF_VAR_devops_gitlab_runtime_enabled=false'
assert_file_contains "${install_script}" 'TF_VAR_devops_forgejo_runtime_enabled=false'
assert_file_contains "${install_script}" 'TF_VAR_office_onlyoffice_runtime_enabled=false'
assert_file_contains "${install_script}" 'TF_VAR_office_nextcloud_integration_mode=nextcloud-onlyoffice-app-behind-backend-facade'
assert_file_contains "${release_env}" 'TF_VAR_groupware_contacts_runtime_enabled=false'
assert_file_contains "${release_env}" 'TF_VAR_groupware_forms_runtime_enabled=false'
assert_file_contains "${release_env}" 'TF_VAR_livekit_runtime_enabled=false'
assert_file_contains "${provider_stack_compose}" 'gitlab/gitlab-ce'
assert_file_contains "${provider_stack_compose}" 'codeberg.org/forgejo/forgejo'
assert_file_contains "${provider_stack_compose}" 'onlyoffice/documentserver'
assert_file_contains "${provider_stack_compose}" 'livekit/livekit-server'
assert_file_contains "${provider_stack_check}" '/providers/status'
assert_file_contains "${provider_stack_check}" 'nextcloud-files'
assert_file_contains "${provider_stack_check}" 'nextcloud-caldav'
assert_file_contains "${provider_stack_check}" 'nextcloud-carddav'
assert_file_contains "${provider_stack_check}" 'nextcloud-forms'
assert_file_contains "${provider_stack_check}" 'synapse-homeserver'
assert_file_contains "${provider_stack_check}" 'matrix-authentication-service'
assert_file_contains "${provider_stack_check}" 'livekit'
assert_file_contains "${provider_stack_check}" 'openproject-primary'
assert_file_contains "${provider_stack_check}" '/office/capabilities'
assert_file_contains "${provider_stack_check}" '/devops/summary'
assert_file_absent "${install_script}" 'WEAVE_DEVOPS_GITLAB_API_TOKEN=%q'
assert_file_absent "${install_script}" 'WEAVE_OFFICE_ONLYOFFICE_JWT_SECRET=%q'
assert_file_absent "${install_script}" 'WEAVE_LIVEKIT_API_KEY=%q'
assert_file_absent "${install_script}" 'WEAVE_LIVEKIT_API_SECRET=%q'
assert_file_contains "${ROOT_DIR}/operator-check.sh" 'assert_backend_provider_stack_config'
assert_file_contains "${ROOT_DIR}/operator-check.sh" 'GitLab CE/FOSS must remain the primary DevOps provider assumption'
assert_file_contains "${support_bundle}" 'WEAVE_DEVOPS_PRIMARY_PROVIDER'
assert_file_contains "${support_bundle}" 'WEAVE_OFFICE_PRIMARY_PROVIDER'
assert_file_contains "${support_bundle}" 'WEAVE_LIVEKIT_ENABLED'
assert_file_contains "${support_bundle}" 'weave-livekit'
assert_file_absent "${support_bundle}" 'WEAVE_LIVEKIT_API_KEY'
assert_file_absent "${support_bundle}" 'WEAVE_LIVEKIT_API_SECRET'

# OpenProject is the first real Boards provider path, but must stay optional, backend-owned, and secret-safe.
assert_file_contains "${backend_main}" 'WEAVE_BOARDS_PROVIDER=${var.boards_provider}'
assert_file_contains "${backend_main}" 'WEAVE_BOARDS_OPENPROJECT_RUNTIME_ENABLED=${var.boards_openproject_runtime_enabled}'
assert_file_contains "${backend_main}" 'WEAVE_BOARDS_OPENPROJECT_READ_SYNC_ENABLED=${var.boards_openproject_read_sync_enabled}'
assert_file_contains "${backend_main}" 'WEAVE_BOARDS_OPENPROJECT_PROVIDER_WRITES_ENABLED=${var.boards_openproject_provider_writes_enabled}'
assert_file_contains "${backend_main}" 'WEAVE_BOARDS_NEXTCLOUD_DECK_RUNTIME_ENABLED=${var.boards_nextcloud_deck_runtime_enabled}'
assert_file_contains "${backend_main}" 'WEAVE_BOARDS_OPENPROJECT_AUTH_MODE=${var.boards_openproject_auth_mode}'
assert_file_contains "${backend_main}" 'WEAVE_BOARDS_OPENPROJECT_BASE_URL=${var.boards_openproject_base_url}'
assert_file_contains "${backend_main}" 'WEAVE_BOARDS_OPENPROJECT_API_TOKEN=${var.boards_openproject_api_token}'
assert_file_contains "${backend_main}" 'WEAVE_CONTEXT_AUTHORIZATION_PRINCIPAL_CLAIM=${var.context_authorization_principal_claim}'
assert_file_contains "${backend_main}" 'WEAVE_CONTEXT_AUTHORIZATION_TENANT_FALLBACK_CLAIM=${var.context_authorization_tenant_fallback_claim}'
assert_file_contains "${backend_main}" 'WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_0_PRINCIPAL_REF=${var.context_authorization_bootstrap_principal_ref}'
assert_file_contains "${backend_main}" 'WEAVE_CONTEXT_AUTHORIZATION_GRAPH_EDGES_0_FROM_CONTEXT_ID=${var.context_authorization_bootstrap_context_id}'
assert_file_contains "${backend_main}" 'WEAVE_CONTEXT_AUTHORIZATION_GRAPH_EDGES_1_TO_CONTEXT_ID=channel-engineering-general'
assert_file_contains "${backend_main}" 'WEAVE_CONTEXT_AUTHORIZATION_GRAPH_EDGES_1_RELATION=CONTAINS'
assert_file_contains "${backend_main}" 'var.context_authorization_bootstrap_enabled ?'
assert_file_contains "${infra_main}" 'boards_provider'
assert_file_contains "${infra_main}" 'boards_openproject_provider_writes_enabled       = var.boards_openproject_provider_writes_enabled'
assert_file_contains "${infra_main}" 'boards_nextcloud_deck_runtime_enabled            = var.boards_nextcloud_deck_runtime_enabled'
assert_file_contains "${infra_main}" 'context_authorization_bootstrap_enabled          = var.context_authorization_bootstrap_enabled'
assert_file_contains "${ROOT_DIR}/01-infrastructure/variables.tf" 'variable "context_authorization_bootstrap_enabled"'
assert_file_contains "${ROOT_DIR}/01-infrastructure/variables.tf" 'default     = "sub"'
assert_file_contains "${ROOT_DIR}/02-keycloak-setup/modules/tenant-identity/main.tf" 'keycloak_openid_hardcoded_claim_protocol_mapper'
assert_file_contains "${ROOT_DIR}/02-keycloak-setup/modules/tenant-identity/main.tf" 'claim_name          = "weave_tenant_id"'
assert_file_contains "${ROOT_DIR}/02-keycloak-setup/modules/tenant-identity/main.tf" 'claim_value         = var.context_authorization_default_tenant_id'
assert_file_contains "${install_script}" 'TF_VAR_boards_openproject_provider_writes_enabled=false'
assert_file_contains "${install_script}" 'TF_VAR_context_authorization_principal_claim=preferred_username'
assert_file_contains "${install_script}" 'TF_VAR_context_authorization_tenant_fallback_claim=tenant_id'
assert_file_contains "${install_script}" 'TF_VAR_context_authorization_bootstrap_principal_ref=user:test'
assert_file_contains "${install_script}" 'set_default_var TF_VAR_context_authorization_bootstrap_enabled true'
assert_file_contains "${install_script}" 'TF_VAR_context_authorization_bootstrap_enabled'
assert_file_contains "${install_script}" 'WEAVE_CONTEXT_AUTHORIZATION_DEFAULT_TENANT_ID'
assert_file_contains "${install_script}" 'WEAVE_CONTEXT_AUTHORIZATION_PRINCIPAL_REF_PREFIX'
assert_file_contains "${install_script}" 'WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_0_TENANT_ID'
assert_file_contains "${install_script}" 'WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_0_SOURCE'
assert_file_contains "${release_env}" 'TF_VAR_boards_openproject_provider_writes_enabled=false'
assert_file_contains "${openproject_compose}" 'profiles:'
assert_file_contains "${openproject_compose}" 'weave-openproject'
assert_file_contains "${openproject_compose}" 'TF_VAR_openproject_secret_key_base'
assert_file_contains "${openproject_doc}" 'OpenProject is a backend/provider engine only'
assert_file_contains "${openproject_doc}" 'TF_VAR_boards_openproject_context_authorization_enabled=true'
assert_file_contains "${openproject_doc}" 'TF_VAR_boards_openproject_provider_writes_enabled=false'
assert_file_contains "${openproject_doc}" 'The backend-held API token is never written to `app-config.env`'
assert_file_contains "${openproject_doc}" 'WEAVE_OPENPROJECT_LIVE_E2E_EXPECT_ENABLED=true'
assert_file_contains "${openproject_live_e2e}" '/boards/workspace'
assert_file_contains "${openproject_live_e2e}" '/providers/status'
assert_file_contains "${openproject_live_e2e}" 'openproject-workspace-sync-backend-facade'
assert_file_contains "${openproject_live_e2e}" 'response leaked OpenProject API token'
assert_file_contains "${openproject_live_e2e}" 'provider writes remain refused'
assert_file_contains "${ROOT_DIR}/smoke-test.sh" '/providers/status'
assert_file_contains "${ROOT_DIR}/smoke-test.sh" '/profile/readiness'
assert_file_contains "${ROOT_DIR}/smoke-test.sh" 'CEFACADE'
assert_file_contains "${ROOT_DIR}/smoke-test.sh" 'member token should receive 403 from admin/provider registry'
assert_file_contains "${ROOT_DIR}/operator-check.sh" '/providers/status'
assert_file_contains "${ROOT_DIR}/operator-check.sh" '/profile/readiness'
assert_file_contains "${ROOT_DIR}/operator-check.sh" 'CEFACADE'
assert_file_contains "${ROOT_DIR}/operator-check.sh" 'member token should receive 403 from admin/provider registry'
assert_file_contains "${REPO_DIR}/.github/workflows/ci.yml" 'openproject-boards-live-e2e.sh'
assert_file_contains "${ROOT_DIR}/operator-check.sh" 'OpenProject workspace sync requires Context/Space authorization gate enabled'
assert_file_absent "${release_env}" 'TF_VAR_boards_openproject_api_token=replace-me'
assert_file_absent "${install_script}" 'WEAVE_BOARDS_OPENPROJECT_API_TOKEN=%q'
assert_file_contains "${connector_doc}" 'Boards provider secrets follow the same rule'

# V01_INFRA_CONTROL_PLANE_BOOTSTRAP: recommended default bootstrap feeds backend control plane safely.
provider_profile_sovereign="${ROOT_DIR}/provider-profiles/sovereign-default.json"
provider_profile_ms="${ROOT_DIR}/provider-profiles/microsoft-hybrid-placeholder.json"
keycloak_realm_contract="${ROOT_DIR}/keycloak/weave-dev-realm-contract.json"
control_plane_bootstrap_doc="${ROOT_DIR}/../../docs/control-plane-infra-bootstrap.md"
for file in "${provider_profile_sovereign}" "${provider_profile_ms}" "${keycloak_realm_contract}" "${control_plane_bootstrap_doc}"; do
  [[ -f "${file}" ]] || fail "Missing control-plane bootstrap artifact: ${file}"
done
assert_file_contains "${provider_profile_sovereign}" '"profileKey": "sovereign-default"'
assert_file_contains "${provider_profile_sovereign}" '"recommended_self_hosted_default"'
assert_file_contains "${provider_profile_sovereign}" '"adminControlPlaneRoute": "/api/admin/control-plane"'
assert_file_contains "${provider_profile_sovereign}" '"secretRefPrefix": "secretref://weave/provider/"'
assert_file_contains "${provider_profile_sovereign}" '"selectedAdapter": "keycloak-realm"'
assert_file_contains "${provider_profile_ms}" '"profileKey": "microsoft-hybrid-placeholder"'
assert_file_contains "${provider_profile_ms}" '"selectedAdapter": "entra-id"'
assert_file_contains "${provider_profile_ms}" '"selectedAdapter": "sharepoint"'
assert_file_contains "${keycloak_realm_contract}" '"clientId": "weave-admin-console"'
assert_file_contains "${keycloak_realm_contract}" '"operator"'
assert_file_contains "${keycloak_realm_contract}" '"rawSecretsIncluded": false'
assert_file_contains "${keycloak_main}" 'weave_admin_console'
assert_file_contains "${keycloak_main}" 'workspace-operators'
assert_file_contains "${keycloak_main}" 'claim_name          = "weave_organization_name"'
assert_file_contains "${install_script}" 'TF_VAR_admin_subdomain=admin'
assert_file_contains "${install_script}" 'WEAVE_ADMIN_CONSOLE_URL'
assert_file_contains "${install_script}" 'WEAVE_ADMIN_CONSOLE_OIDC_CLIENT_ID'
assert_file_contains "${install_script}" 'WEAVE_ORG_MANIFEST_URL'
assert_file_contains "${support_bundle}" 'WEAVE_ADMIN_CONSOLE_URL'
assert_file_contains "${support_bundle}" 'WEAVE_PROVIDER_PROFILE'
assert_file_contains "${ROOT_DIR}/smoke-test.sh" 'Checking admin API protection with a member token'
assert_file_contains "${ROOT_DIR}/smoke-test.sh" '/admin/control-plane'
assert_file_contains "${ROOT_DIR}/operator-check.sh" 'member token should receive 403 from admin control plane'
assert_file_contains "${caddy_template}" 'Weave Organization/Admin Console deploy target'
assert_file_absent "${provider_profile_sovereign}" 'client_secret'
assert_file_absent "${provider_profile_sovereign}" 'api_token'
assert_file_absent "${provider_profile_ms}" 'client_secret'
assert_file_absent "${keycloak_realm_contract}" 'replace-me'

# Sprint 32 local dogfood is DNS-first: local_lan_host may support cert SANs,
# but it must not become a second app/startup/issuer/CA URL truth.
assert_file_contains "${infra_main}" 'client_public_url           = local.public_urls.weave'
assert_file_contains "${infra_main}" 'client_api_origin           = local.public_urls.api'
assert_file_contains "${infra_main}" 'client_auth_url             = local.public_urls.auth'
assert_file_contains "${infra_main}" 'client_matrix_url           = local.public_urls.matrix'
assert_file_contains "${infra_main}" 'local_lan_host is a deprecated'
assert_file_contains "${infra_outputs}" 'WEAVE_LOCAL_CA_URL'
assert_file_contains "${install_script}" 'export WEAVE_LOCAL_CA_URL'
assert_file_contains "${install_script}" 'http://${TF_VAR_tenant_domain}:${TF_VAR_proxy_http_host_port}/weave-local-ca.pem'
assert_file_contains "${install_script}" '- Local CA:   http://${TF_VAR_tenant_domain}:${TF_VAR_proxy_http_host_port}/weave-local-ca.pem'
assert_file_contains "${install_script}" 'restore_default_local_tls_from_state'
assert_file_contains "${install_script}" 'persist_default_local_tls_to_state'
assert_file_contains "${install_script}" 'WEAVE_LOCAL_TLS_STATE_DIR'
assert_file_contains "${infra_main}" 'tls_certs_dir      = local.caddy_certs_dir'
assert_file_contains "${ROOT_DIR}/01-infrastructure/modules/reverse-proxy/main.tf" 'host_path      = var.tls_certs_dir'
assert_file_contains "${ROOT_DIR}/01-infrastructure/modules/reverse-proxy/main.tf" 'container_path = "/certs"'
assert_file_contains "${ROOT_DIR}/smoke-test.sh" 'local_lan_host is non-canonical'
assert_file_contains "${ROOT_DIR}/operator-check.sh" 'local_lan_host is non-canonical'
assert_file_contains "${caddy_template}" 'http://${ca_bootstrap_host}'
assert_file_contains "${caddy_template}" '<h1>Weave Local Dogfood start</h1>'
assert_file_contains "${caddy_template}" 'Weave Local Development CA'
assert_file_contains "${caddy_template}" '${ca_bootstrap_url}/weave-local-ca.pem'
assert_file_contains "${caddy_template}" '${client_public_url}/weave-local-ca.pem'
assert_file_contains "${caddy_template}" '@product_api path /api/*'
assert_file_contains "${caddy_template}" 'reverse_proxy ${api_upstream}'
assert_file_contains "${infra_main}" 'client_public_url     = local.client_public_url'
assert_file_contains "${caddy_template}" 'handoff-s32-massimo-dogfood-home'
assert_file_contains "${caddy_template}" 'passwords, tokens, client secrets, or credential URLs'
assert_file_contains "${local_invite_script}" 'base_url="${WEAVE_PUBLIC_BASE_URL:-https://weave.test:44443}"'
assert_file_contains "${local_invite_script}" 'handoff-s32-massimo-dogfood-home'
assert_file_contains "${local_invite_script}" 'json.dumps(result, separators=(",", ":"), sort_keys=True)'
assert_file_contains "${dogfood_handoff_bundle}" 'weave.dogfood.handoff-bundle.v1'
assert_file_contains "${dogfood_handoff_bundle}" 'profile-or-release'
assert_file_contains "${dogfood_ios_smoke}" 'debug builds are invalid'
assert_file_contains "${dogfood_ios_smoke}" 'last_handoff_consumed_v1'
assert_file_contains "${dogfood_cert_smoke}" 'weave.dogfood.cert-persistence-smoke.v1'
assert_file_absent "${install_script}" 'http://${TF_VAR_local_lan_host}:${TF_VAR_proxy_http_host_port}/weave-local-ca.pem'
assert_file_absent "${infra_outputs}" 'local_lan_url'

invite_json="$(${local_invite_script} --json)"
printf '%s' "${invite_json}" | jq -e '
  .inviteLink == "https://weave.test:44443/join?handoff_ref=handoff-s32-massimo-dogfood-home&org=massimo-dogfood&workspace=home&profile=local-lan-dogfood&run_id=s32-massimo-dogfood" and
  .qrPayload == .inviteLink and
  .platformConfigUrl == "https://weave.test:44443/api/platform/config" and
  .org == "massimo-dogfood" and
  .workspace == "home" and
  (.secretPolicy | contains(".generated/bootstrap.env"))
' >/dev/null || fail "Default local invite JSON does not match the no-secret DNS-first handoff contract"
printf '%s' "${invite_json}" | grep -Eiq '127\.0\.0\.1|localhost|192\.168\.|password=|token=|secret=' && \
  fail "Default local invite JSON leaked non-DNS or credential-bearing data"

printf '%s\n' 'infra product contract tests passed'
