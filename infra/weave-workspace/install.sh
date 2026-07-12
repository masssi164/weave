#!/usr/bin/env bash
# shellcheck shell=bash
# shellcheck disable=SC2154

set -euo pipefail

: "${WEAVE_IAC_BIN:=tofu}"
export WEAVE_IAC_BIN

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly ROOT_DIR
readonly INFRA_DIR="${ROOT_DIR}/01-infrastructure"
readonly KEYCLOAK_DIR="${ROOT_DIR}/02-keycloak-setup"
readonly BOOTSTRAP_ENV_FILE="${ROOT_DIR}/.generated/bootstrap.env"
readonly APP_CONFIG_ENV_FILE="${ROOT_DIR}/.generated/app-config.env"
readonly RUNNER_BOOTSTRAP_ENV_FILE="/tmp/weave-infra/weave-workspace/.generated/bootstrap.env"
readonly TEARDOWN_SCRIPT="${ROOT_DIR}/teardown.sh"
readonly SYNAPSE_VOLUME_HELPER="${ROOT_DIR}/lib/synapse-volume.sh"
readonly LOOPBACK_HOST="${WEAVE_LOOPBACK_HOST:-127.0.0.1}"
LOOPBACK_RESOLVE_HOST="${WEAVE_LOOPBACK_RESOLVE_HOST:-${LOOPBACK_HOST}}"
readonly TEST_USER_EMAIL="test@weave.test"
readonly PERSISTED_TF_VARS=(
  TF_VAR_docker_host
  TF_VAR_docker_network_name
  TF_VAR_tenant_slug
  TF_VAR_tenant_domain
  TF_VAR_local_lan_host
  TF_VAR_create_test_user
  TF_VAR_test_user_password
  TF_VAR_auth_subdomain
  TF_VAR_api_subdomain
  TF_VAR_matrix_subdomain
  TF_VAR_nextcloud_subdomain
  TF_VAR_public_scheme
  TF_VAR_proxy_host_port
  TF_VAR_proxy_http_host_port
  TF_VAR_keycloak_host_port
  TF_VAR_keycloak_management_host_port
  TF_VAR_mas_host_port
  TF_VAR_synapse_host_port
  TF_VAR_nextcloud_host_port
  TF_VAR_nextcloud_trusted_proxies
  TF_VAR_caddy_tls_cert_file
  TF_VAR_caddy_tls_key_file
  TF_VAR_caddy_tls_ca_file
  TF_VAR_backend_host_port
  TF_VAR_backend_container_port
  TF_VAR_weave_backend_image
  TF_VAR_mcp_host_port
  TF_VAR_mcp_container_port
  TF_VAR_weave_mcp_server_image
  TF_VAR_provider_stack_profile
  TF_VAR_provider_stack_readiness
  TF_VAR_devops_primary_provider
  TF_VAR_devops_gitlab_runtime_enabled
  TF_VAR_devops_gitlab_base_url
  TF_VAR_devops_gitlab_writes_enabled
  TF_VAR_office_primary_provider
  TF_VAR_office_onlyoffice_runtime_enabled
  TF_VAR_office_onlyoffice_document_server_url
  TF_VAR_office_nextcloud_integration_mode
  TF_VAR_office_collabora_runtime_enabled
  TF_VAR_groupware_contacts_runtime_enabled
  TF_VAR_groupware_forms_runtime_enabled
  TF_VAR_livekit_runtime_enabled
  TF_VAR_livekit_url
  TF_VAR_livekit_token_endpoint
  TF_VAR_livekit_image
  TF_VAR_livekit_host_port
  TF_VAR_livekit_rtc_tcp_host_port
  TF_VAR_livekit_rtc_udp_host_port
  TF_VAR_boards_runtime_enabled
  TF_VAR_boards_provider
  TF_VAR_boards_openproject_runtime_enabled
  TF_VAR_boards_openproject_read_sync_enabled
  TF_VAR_boards_openproject_context_authorization_enabled
  TF_VAR_boards_openproject_audit_consent_enabled
  TF_VAR_boards_openproject_provider_writes_enabled
  TF_VAR_boards_nextcloud_deck_runtime_enabled
  TF_VAR_boards_openproject_auth_mode
  TF_VAR_boards_openproject_base_url
  TF_VAR_context_authorization_tenant_claim
  TF_VAR_context_authorization_tenant_fallback_claim
  TF_VAR_context_authorization_default_tenant_id
  TF_VAR_context_authorization_principal_claim
  TF_VAR_context_authorization_principal_ref_prefix
  TF_VAR_context_authorization_bootstrap_enabled
  TF_VAR_context_authorization_bootstrap_context_id
  TF_VAR_context_authorization_bootstrap_principal_ref
  TF_VAR_context_authorization_dogfood_principal_ref
  TF_VAR_context_authorization_bootstrap_role
  TF_VAR_openproject_image
  TF_VAR_openproject_host_port
  TF_VAR_openproject_secret_key_base
  TF_VAR_synapse_uid
  TF_VAR_synapse_gid
  TF_VAR_db_name
  TF_VAR_db_admin_username
  TF_VAR_db_admin_password
  TF_VAR_backend_db_username
  TF_VAR_backend_db_password
  TF_VAR_keycloak_admin_username
  TF_VAR_keycloak_admin_password
  TF_VAR_keycloak_db_username
  TF_VAR_keycloak_db_password
  TF_VAR_mas_db_username
  TF_VAR_mas_db_password
  TF_VAR_synapse_db_username
  TF_VAR_synapse_db_password
  TF_VAR_nextcloud_db_username
  TF_VAR_nextcloud_db_password
  TF_VAR_nextcloud_admin_username
  TF_VAR_nextcloud_admin_password
  TF_VAR_nextcloud_backend_actor_username
  TF_VAR_nextcloud_backend_actor_token
  TF_VAR_matrix_mas_client_secret
  TF_VAR_identity_admin_client_secret
  TF_VAR_identity_events_hmac_secret
  TF_VAR_mas_encryption_secret
  TF_VAR_mas_signing_key_pem
  TF_VAR_mas_matrix_secret
  TF_VAR_synapse_registration_shared_secret
  TF_VAR_synapse_macaroon_secret_key
  TF_VAR_synapse_form_secret
  WEAVE_MATRIX_PROVISIONER_LOCALPART
  WEAVE_MATRIX_PROVISIONER_PASSWORD
  WEAVE_MATRIX_PROVISIONER_ACCESS_TOKEN
  WEAVE_MATRIX_DEFAULT_MEMBER_LOCALPART
  WEAVE_MATRIX_DEFAULT_MEMBER_PASSWORD
  WEAVE_MATRIX_DEFAULT_MEMBER_ACCESS_TOKEN
  WEAVE_MATRIX_WORKSPACE_ALIAS_LOCALPART
  WEAVE_MATRIX_WORKSPACE_NAME
  WEAVE_MATRIX_ANNOUNCEMENTS_ALIAS_LOCALPART
  WEAVE_MATRIX_GENERAL_ALIAS_LOCALPART
  WEAVE_MATRIX_HELP_ALIAS_LOCALPART
  WEAVE_MATRIX_DEFAULT_SPACE_ID
  WEAVE_MATRIX_DEFAULT_ANNOUNCEMENTS_ID
  WEAVE_MATRIX_DEFAULT_GENERAL_ID
  WEAVE_MATRIX_DEFAULT_HELP_ID
)

log() {
  printf '%s\n' "$*"
}

fail() {
  printf '%s\n' "$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing required command: $1"
}

detect_docker_host() {
  if [[ -n "${DOCKER_HOST:-}" ]]; then
    printf '%s\n' "${DOCKER_HOST}"
    return
  fi

  docker context inspect "$(docker context show)" --format '{{ (index .Endpoints "docker").Host }}'
}

set_default_var() {
  local name="$1"
  local value="$2"

  if [[ -z "${!name:-}" ]]; then
    export "${name}=${value}"
  fi
}

set_default_secret() {
  local name="$1"
  local value="$2"

  if [[ -z "${!name:-}" ]]; then
    export "${name}=${value}"
  fi
}

random_base64() {
  local bytes="$1"
  openssl rand -base64 "${bytes}" | tr -d '\n'
}

random_hex() {
  local bytes="$1"
  openssl rand -hex "${bytes}"
}

normalize_repo_local_cert_path_var() {
  local name="$1"
  local value="${!name:-}"
  local repo_generated_suffix="/weave-workspace/01-infrastructure/.generated/caddy/certs/"

  if [[ -z "${value}" || "${value}" != *"${repo_generated_suffix}"* ]]; then
    return
  fi

  export "${name}=${INFRA_DIR}/.generated/caddy/certs/$(basename -- "${value}")"
}

normalize_repo_local_paths() {
  normalize_repo_local_cert_path_var TF_VAR_caddy_tls_cert_file
  normalize_repo_local_cert_path_var TF_VAR_caddy_tls_key_file
  normalize_repo_local_cert_path_var TF_VAR_caddy_tls_ca_file
}

local_tls_state_dir() {
  if [[ "${WEAVE_LOCAL_TLS_STATE_DIR:-}" == "none" ]]; then
    return 1
  fi

  printf '%s\n' "${WEAVE_LOCAL_TLS_STATE_DIR:-${XDG_STATE_HOME:-${HOME}/.local/state}/weave/dogfood/caddy/certs}"
}

using_default_local_tls_paths() {
  local generated_dir="${INFRA_DIR}/.generated/caddy/certs"

  [[ "${TF_VAR_caddy_tls_cert_file}" == "${generated_dir}/weave.test.pem" ]] &&
    [[ "${TF_VAR_caddy_tls_key_file}" == "${generated_dir}/weave.test-key.pem" ]] &&
    [[ "${TF_VAR_caddy_tls_ca_file}" == "${generated_dir}/weave-local-ca.pem" ]]
}

restore_default_local_tls_from_state() {
  using_default_local_tls_paths || return 0

  local state_dir
  state_dir="$(local_tls_state_dir)" || return 0
  [[ -d "${state_dir}" ]] || return 0

  local file
  for file in \
    weave.test.pem \
    weave.test-key.pem \
    weave-local-ca.pem \
    weave-local-ca-key.pem \
    weave-local-ca.srl; do
    if [[ -f "${state_dir}/${file}" && ! -f "${INFRA_DIR}/.generated/caddy/certs/${file}" ]]; then
      mkdir -p "${INFRA_DIR}/.generated/caddy/certs"
      cp "${state_dir}/${file}" "${INFRA_DIR}/.generated/caddy/certs/${file}"
    fi
  done
}

persist_default_local_tls_to_state() {
  using_default_local_tls_paths || return 0

  local state_dir
  state_dir="$(local_tls_state_dir)" || return 0
  mkdir -p "${state_dir}"
  chmod 700 "${state_dir}"

  local file
  for file in \
    weave.test.pem \
    weave.test-key.pem \
    weave-local-ca.pem \
    weave-local-ca-key.pem \
    weave-local-ca.srl; do
    if [[ -f "${INFRA_DIR}/.generated/caddy/certs/${file}" ]]; then
      cp "${INFRA_DIR}/.generated/caddy/certs/${file}" "${state_dir}/${file}"
    fi
  done

  chmod 600 "${state_dir}"/*-key.pem 2>/dev/null || true
  chmod 644 "${state_dir}"/*.pem 2>/dev/null || true
}

load_persisted_env() {
  if [[ ! -f "${BOOTSTRAP_ENV_FILE}" ]]; then
    return
  fi

  local var
  local index
  local -a preset_names=()
  local -a preset_values=()

  for var in "${PERSISTED_TF_VARS[@]}"; do
    if [[ "${!var+x}" == "x" ]]; then
      preset_names+=("${var}")
      preset_values+=("${!var}")
    fi
  done

  # shellcheck disable=SC1090
  source "${BOOTSTRAP_ENV_FILE}"
  normalize_repo_local_paths

  for ((index = 0; index < ${#preset_names[@]}; index++)); do
    export "${preset_names[$index]}=${preset_values[$index]}"
  done

}

persist_bootstrap_env() {
  local var

  mkdir -p "$(dirname -- "${BOOTSTRAP_ENV_FILE}")" "$(dirname -- "${RUNNER_BOOTSTRAP_ENV_FILE}")"
  : > "${BOOTSTRAP_ENV_FILE}"
  chmod 600 "${BOOTSTRAP_ENV_FILE}"

  for var in "${PERSISTED_TF_VARS[@]}"; do
    if [[ "${!var+x}" == "x" ]]; then
      printf 'export %s=%q\n' "${var}" "${!var}" >> "${BOOTSTRAP_ENV_FILE}"
    fi
  done

  {
    printf 'export WEAVE_PUBLIC_BASE_URL=%q\n' "$(client_public_url)"
    printf 'export WEAVE_API_ORIGIN=%q\n' "$(client_api_origin_url)"
    printf 'export WEAVE_API_BASE_URL=%q\n' "$(integration_test_base_url)"
    printf 'export WEAVE_BASE_URL=%q\n' "$(integration_test_base_url)"
    printf 'export WEAVE_AUTH_BASE_URL=%q\n' "$(client_auth_public_url)"
    printf 'export WEAVE_ADMIN_CONSOLE_URL=%q\n' "$(admin_public_url)"
    printf 'export WEAVE_ADMIN_CONSOLE_OIDC_CLIENT_ID=%q\n' "weave-admin-console"
    printf 'export WEAVE_ORG_MANIFEST_URL=%q\n' "$(integration_test_base_url)/organization/manifest"
    printf 'export WEAVE_PROVIDER_PROFILE=%q\n' "${TF_VAR_provider_stack_profile}"
    printf 'export WEAVE_FILES_PRODUCT_URL=%q\n' "$(client_public_url)/files"
    printf 'export WEAVE_CALENDAR_PRODUCT_URL=%q\n' "$(client_public_url)/calendar"
    printf 'export WEAVE_LOCAL_CA_URL=%q\n' "http://${TF_VAR_tenant_domain}:${TF_VAR_proxy_http_host_port}/weave-local-ca.pem"
    printf 'export WEAVE_NEXTCLOUD_BASE_URL=%q\n' "${TF_VAR_public_scheme}://$(public_host "${TF_VAR_nextcloud_subdomain}")$(public_port_suffix)"
    printf 'export WEAVE_NEXTCLOUD_TECHNICAL_BASE_URL=%q\n' "${TF_VAR_public_scheme}://$(public_host "${TF_VAR_nextcloud_subdomain}")$(public_port_suffix)"
    printf 'export WEAVE_NEXTCLOUD_FILES_ACTOR_MODEL=%q\n' "backend-service-account"
    printf 'export WEAVE_NEXTCLOUD_FILES_ACTOR_USERNAME=%q\n' "${TF_VAR_nextcloud_backend_actor_username}"
    printf 'export WEAVE_NEXTCLOUD_FILES_ACTOR_TOKEN=%q\n' "${TF_VAR_nextcloud_backend_actor_token}"
    printf 'export WEAVE_NEXTCLOUD_FILES_WEBDAV_ROOT_PATH=%q\n' "/remote.php/dav/files"
    printf 'export WEAVE_CALDAV_BASE_URL=%q\n' "${TF_VAR_public_scheme}://$(public_host "${TF_VAR_nextcloud_subdomain}")$(public_port_suffix)"
    printf 'export WEAVE_CALDAV_CALENDAR_PATH_TEMPLATE=%q\n' "/remote.php/dav/calendars/${TF_VAR_nextcloud_backend_actor_username}/personal/"
    printf 'export WEAVE_CALDAV_AUTH_MODE=%q\n' "BASIC"
    printf 'export WEAVE_CALDAV_BACKEND_USERNAME=%q\n' "${TF_VAR_nextcloud_backend_actor_username}"
    printf 'export WEAVE_CALDAV_BACKEND_TOKEN=%q\n' "${TF_VAR_nextcloud_backend_actor_token}"
    printf 'export WEAVE_CALDAV_REQUEST_TIMEOUT_SECONDS=%q\n' "10"
    printf 'export WEAVE_CALDAV_EXTERNAL_DISCOVERY_URL=%q\n' "${TF_VAR_public_scheme}://$(public_host "${TF_VAR_nextcloud_subdomain}")$(public_port_suffix)/remote.php/dav"
    printf 'export WEAVE_CALDAV_EXTERNAL_CREDENTIAL_MODE=%q\n' "nextcloud-login-flow-app-password"
    printf 'export WEAVE_CALDAV_EXTERNAL_PROFILE_PASSWORD_MODE=%q\n' "omit"
    printf 'export WEAVE_CALDAV_EXTERNAL_PRIVATE_USER_CALENDARS=%q\n' "disabled"
    printf 'export WEAVE_PROVIDER_STACK_PROFILE=%q\n' "${TF_VAR_provider_stack_profile}"
    printf 'export WEAVE_PROVIDER_STACK_READINESS=%q\n' "${TF_VAR_provider_stack_readiness}"
    printf 'export WEAVE_DEVOPS_PRIMARY_PROVIDER=%q\n' "${TF_VAR_devops_primary_provider}"
    printf 'export WEAVE_DEVOPS_GITLAB_RUNTIME_ENABLED=%q\n' "${TF_VAR_devops_gitlab_runtime_enabled}"
    printf 'export WEAVE_DEVOPS_GITLAB_BASE_URL=%q\n' "${TF_VAR_devops_gitlab_base_url}"
    printf 'export WEAVE_DEVOPS_GITLAB_WRITES_ENABLED=%q\n' "${TF_VAR_devops_gitlab_writes_enabled}"
    printf 'export WEAVE_OFFICE_PRIMARY_PROVIDER=%q\n' "${TF_VAR_office_primary_provider}"
    printf 'export WEAVE_OFFICE_ONLYOFFICE_RUNTIME_ENABLED=%q\n' "${TF_VAR_office_onlyoffice_runtime_enabled}"
    printf 'export WEAVE_OFFICE_ONLYOFFICE_DOCUMENT_SERVER_URL=%q\n' "${TF_VAR_office_onlyoffice_document_server_url}"
    printf 'export WEAVE_OFFICE_NEXTCLOUD_INTEGRATION_MODE=%q\n' "${TF_VAR_office_nextcloud_integration_mode}"
    printf 'export WEAVE_OFFICE_COLLABORA_RUNTIME_ENABLED=%q\n' "${TF_VAR_office_collabora_runtime_enabled}"
    printf 'export WEAVE_GROUPWARE_CONTACTS_RUNTIME_ENABLED=%q\n' "${TF_VAR_groupware_contacts_runtime_enabled}"
    printf 'export WEAVE_GROUPWARE_FORMS_RUNTIME_ENABLED=%q\n' "${TF_VAR_groupware_forms_runtime_enabled}"
    printf 'export WEAVE_LIVEKIT_ENABLED=%q\n' "${TF_VAR_livekit_runtime_enabled}"
    printf 'export WEAVE_LIVEKIT_URL=%q\n' "${TF_VAR_livekit_url}"
    printf 'export WEAVE_LIVEKIT_TOKEN_ENDPOINT=%q\n' "${TF_VAR_livekit_token_endpoint}"
    printf 'export WEAVE_BOARDS_RUNTIME_ENABLED=%q\n' "${TF_VAR_boards_runtime_enabled}"
    printf 'export WEAVE_BOARDS_PROVIDER=%q\n' "${TF_VAR_boards_provider}"
    printf 'export WEAVE_BOARDS_OPENPROJECT_RUNTIME_ENABLED=%q\n' "${TF_VAR_boards_openproject_runtime_enabled}"
    printf 'export WEAVE_BOARDS_OPENPROJECT_READ_SYNC_ENABLED=%q\n' "${TF_VAR_boards_openproject_read_sync_enabled}"
    printf 'export WEAVE_BOARDS_OPENPROJECT_CONTEXT_AUTHORIZATION_ENABLED=%q\n' "${TF_VAR_boards_openproject_context_authorization_enabled}"
    printf 'export WEAVE_BOARDS_OPENPROJECT_AUDIT_CONSENT_ENABLED=%q\n' "${TF_VAR_boards_openproject_audit_consent_enabled}"
    printf 'export WEAVE_BOARDS_OPENPROJECT_PROVIDER_WRITES_ENABLED=%q\n' "${TF_VAR_boards_openproject_provider_writes_enabled}"
    printf 'export WEAVE_BOARDS_NEXTCLOUD_DECK_RUNTIME_ENABLED=%q\n' "${TF_VAR_boards_nextcloud_deck_runtime_enabled}"
    printf 'export WEAVE_BOARDS_OPENPROJECT_AUTH_MODE=%q\n' "${TF_VAR_boards_openproject_auth_mode}"
    printf 'export WEAVE_BOARDS_OPENPROJECT_BASE_URL=%q\n' "${TF_VAR_boards_openproject_base_url}"
    printf 'export WEAVE_CONTEXT_AUTHORIZATION_TENANT_CLAIM=%q\n' "${TF_VAR_context_authorization_tenant_claim}"
    printf 'export WEAVE_CONTEXT_AUTHORIZATION_TENANT_FALLBACK_CLAIM=%q\n' "${TF_VAR_context_authorization_tenant_fallback_claim}"
    printf 'export WEAVE_CONTEXT_AUTHORIZATION_DEFAULT_TENANT_ID=%q\n' "${TF_VAR_context_authorization_default_tenant_id}"
    printf 'export WEAVE_CONTEXT_AUTHORIZATION_PRINCIPAL_CLAIM=%q\n' "${TF_VAR_context_authorization_principal_claim}"
    printf 'export WEAVE_CONTEXT_AUTHORIZATION_PRINCIPAL_REF_PREFIX=%q\n' "${TF_VAR_context_authorization_principal_ref_prefix}"
    if [[ "${TF_VAR_context_authorization_bootstrap_enabled}" == "true" ]]; then
      printf 'export WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_0_TENANT_ID=%q\n' "${TF_VAR_context_authorization_default_tenant_id}"
      printf 'export WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_0_CONTEXT_ID=%q\n' "${TF_VAR_context_authorization_bootstrap_context_id}"
      printf 'export WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_0_PRINCIPAL_REF=%q\n' "${TF_VAR_context_authorization_bootstrap_principal_ref}"
      printf 'export WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_0_ROLE=%q\n' "${TF_VAR_context_authorization_bootstrap_role}"
      printf 'export WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_0_SOURCE=%q\n' "local-dev-bootstrap"
      if [[ -n "${TF_VAR_context_authorization_dogfood_principal_ref}" ]]; then
        printf 'export WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_1_TENANT_ID=%q\n' "${TF_VAR_context_authorization_default_tenant_id}"
        printf 'export WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_1_CONTEXT_ID=%q\n' "${TF_VAR_context_authorization_bootstrap_context_id}"
        printf 'export WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_1_PRINCIPAL_REF=%q\n' "${TF_VAR_context_authorization_dogfood_principal_ref}"
        printf 'export WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_1_ROLE=%q\n' "${TF_VAR_context_authorization_bootstrap_role}"
        printf 'export WEAVE_CONTEXT_AUTHORIZATION_MEMBERSHIPS_1_SOURCE=%q\n' "local-dogfood-bootstrap"
      fi
    fi
    printf 'export WEAVE_MATRIX_HOMESERVER_URL=%q\n' "$(client_matrix_facade_url)"
    printf 'export WEAVE_MATRIX_PROVIDER_URL=%q\n' "$(matrix_provider_public_url)"
    printf 'export WEAVE_OIDC_ISSUER_URL=%q\n' "$(integration_test_oidc_issuer_url)"
    printf 'export WEAVE_OIDC_CLIENT_ID=%q\n' "weave-app"
    printf 'export WEAVE_TARGET_MOBILE=%q\n' "true"
    printf 'export WEAVE_TARGET_DESKTOP=%q\n' "true"
    printf 'export WEAVE_TARGET_WEB=%q\n' "false"
    printf 'export WEAVE_MATRIX_FEDERATION=%q\n' "disabled"
    printf 'export WEAVE_CHAT_E2EE=%q\n' "active-architecture-gated"
  } >> "${BOOTSTRAP_ENV_FILE}"

  if create_test_user_enabled; then
    {
      printf 'export WEAVE_TEST_USERNAME=%q\n' "${TEST_USER_EMAIL}"
      printf 'export WEAVE_TEST_PASSWORD=%q\n' "${TF_VAR_test_user_password}"
    } >> "${BOOTSTRAP_ENV_FILE}"
  fi

  cp "${BOOTSTRAP_ENV_FILE}" "${RUNNER_BOOTSTRAP_ENV_FILE}"
  chmod 600 "${RUNNER_BOOTSTRAP_ENV_FILE}"

  write_app_config_summary
}

ensure_mas_signing_key() {
  if [[ -n "${TF_VAR_mas_signing_key_pem:-}" ]]; then
    export TF_VAR_mas_signing_key_pem
    return
  fi

  local key_file
  key_file="$(mktemp)"

  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "${key_file}" >/dev/null 2>&1
  TF_VAR_mas_signing_key_pem="$(<"${key_file}")"
  export TF_VAR_mas_signing_key_pem
  rm -f -- "${key_file}"
}

wait_for_http_200() {
  local name="$1"
  local url="$2"
  local attempts="${3:-120}"
  local sleep_seconds="${4:-5}"
  local status_code

  for ((i = 1; i <= attempts; i++)); do
    status_code="$(curl -s -o /dev/null -w '%{http_code}' "${url}" || true)"
    if [[ "${status_code}" == "200" ]]; then
      return 0
    fi
    if ((i == 1 || i % 10 == 0)); then
      log "Still waiting for ${name} readiness (attempt ${i}/${attempts}, status ${status_code:-000})..."
    fi
    sleep "${sleep_seconds}"
  done

  fail "${name} never became ready at ${url}"
}

wait_for_keycloak_admin_login() {
  local attempts="${1:-60}"
  local sleep_seconds="${2:-2}"
  local token_url="http://${LOOPBACK_HOST}:${TF_VAR_keycloak_host_port}/realms/master/protocol/openid-connect/token"
  local response

  for ((i = 1; i <= attempts; i++)); do
    response="$(curl -sS -X POST "${token_url}"       -H 'Content-Type: application/x-www-form-urlencoded'       --data-urlencode 'client_id=admin-cli'       --data-urlencode "username=${TF_VAR_keycloak_admin_username}"       --data-urlencode "password=${TF_VAR_keycloak_admin_password}"       --data-urlencode 'grant_type=password' || true)"
    if [[ "${response}" == *'"access_token"'* ]]; then
      return 0
    fi
    sleep "${sleep_seconds}"
  done

  fail "Keycloak admin login never became ready at ${token_url} for user ${TF_VAR_keycloak_admin_username}"
}


keycloak_admin_token() {
  local token_url="http://${LOOPBACK_HOST}:${TF_VAR_keycloak_host_port}/realms/master/protocol/openid-connect/token"
  local response=""
  local token=""

  response="$(curl -fsS -X POST "${token_url}" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode 'client_id=admin-cli' \
    --data-urlencode "username=${TF_VAR_keycloak_admin_username}" \
    --data-urlencode "password=${TF_VAR_keycloak_admin_password}" \
    --data-urlencode 'grant_type=password')"
  token="$(python3 -c 'import json,sys; print(json.load(sys.stdin).get("access_token", ""))' <<<"${response}")"
  [[ -n "${token}" ]] || fail "Keycloak admin token response did not contain an access_token."
  printf '%s' "${token}"
}

keycloak_state_has() {
  local address="$1"

  "${WEAVE_IAC_BIN}" -chdir="${KEYCLOAK_DIR}" state show "${address}" >/dev/null 2>&1
}

keycloak_import_if_missing() {
  local address="$1"
  local import_id="$2"

  if keycloak_state_has "${address}"; then
    return
  fi

  if [[ -z "${import_id}" || "${import_id}" == */ || "${import_id}" == *'//'* ]]; then
    return
  fi

  log "Importing existing Keycloak resource ${address} into OpenTofu state..."
  "${WEAVE_IAC_BIN}" -chdir="${KEYCLOAK_DIR}" import -input=false "${address}" "${import_id}"
}

keycloak_admin_get() {
  local path="$1"
  local token=""

  token="$(keycloak_admin_token)"
  curl -fsS \
    -H "Authorization: Bearer ${token}" \
    "http://${LOOPBACK_HOST}:${TF_VAR_keycloak_host_port}${path}"
}

keycloak_admin_get_query() {
  local path="$1"
  local query_key="$2"
  local query_value="$3"
  local token=""

  token="$(keycloak_admin_token)"
  curl -fsS -G \
    -H "Authorization: Bearer ${token}" \
    --data-urlencode "${query_key}=${query_value}" \
    "http://${LOOPBACK_HOST}:${TF_VAR_keycloak_host_port}${path}"
}

keycloak_json_id_by_field() {
  local field="$1"
  local value="$2"

  python3 -c 'import json,sys
field, value = sys.argv[1], sys.argv[2]
data = json.load(sys.stdin)
if isinstance(data, dict):
    data = [data]
for item in data:
    if item.get(field) == value:
        print(item.get("id", ""))
        break
' "${field}" "${value}"
}

keycloak_json_group_id_by_name() {
  local name="$1"

  python3 -c 'import json,sys
name = sys.argv[1]
def walk(groups):
    for group in groups:
        if group.get("name") == name:
            print(group.get("id", ""))
            return True
        if walk(group.get("subGroups") or []):
            return True
    return False
walk(json.load(sys.stdin))
' "${name}"
}

keycloak_realm_exists() {
  keycloak_admin_get "/admin/realms/${TF_VAR_tenant_slug}" >/dev/null 2>&1
}

keycloak_lookup_client_uuid() {
  local client_id="$1"

  keycloak_admin_get_query "/admin/realms/${TF_VAR_tenant_slug}/clients" clientId "${client_id}" |
    keycloak_json_id_by_field clientId "${client_id}"
}

keycloak_lookup_client_scope_id() {
  local name="$1"

  keycloak_admin_get "/admin/realms/${TF_VAR_tenant_slug}/client-scopes" |
    keycloak_json_id_by_field name "${name}"
}

keycloak_lookup_group_id() {
  local name="$1"

  keycloak_admin_get_query "/admin/realms/${TF_VAR_tenant_slug}/groups" search "${name}" |
    keycloak_json_group_id_by_name "${name}"
}

keycloak_lookup_role_id() {
  local name="$1"

  keycloak_admin_get "/admin/realms/${TF_VAR_tenant_slug}/roles/${name}" 2>/dev/null |
    keycloak_json_id_by_field name "${name}" || true
}

keycloak_lookup_user_id() {
  local username="$1"

  keycloak_admin_get_query "/admin/realms/${TF_VAR_tenant_slug}/users" username "${username}" |
    keycloak_json_id_by_field username "${username}"
}

keycloak_lookup_client_scope_mapper_id() {
  local client_scope_id="$1"
  local name="$2"

  keycloak_admin_get "/admin/realms/${TF_VAR_tenant_slug}/client-scopes/${client_scope_id}/protocol-mappers/models" |
    keycloak_json_id_by_field name "${name}"
}

keycloak_lookup_client_mapper_id() {
  local client_uuid="$1"
  local name="$2"

  keycloak_admin_get "/admin/realms/${TF_VAR_tenant_slug}/clients/${client_uuid}/protocol-mappers/models" |
    keycloak_json_id_by_field name "${name}"
}

ensure_existing_keycloak_terraform_state() {
  local client_scope_id=""
  local mapper_id=""
  local uuid=""

  "${WEAVE_IAC_BIN}" -chdir="${KEYCLOAK_DIR}" init -input=false

  if "${WEAVE_IAC_BIN}" -chdir="${KEYCLOAK_DIR}" providers 2>/dev/null | grep -q 'provider\[registry.opentofu.org/mrparkers/keycloak\]'; then
    "${WEAVE_IAC_BIN}" -chdir="${KEYCLOAK_DIR}" state replace-provider -auto-approve \
      registry.opentofu.org/mrparkers/keycloak \
      registry.opentofu.org/keycloak/keycloak
  fi

  if ! keycloak_realm_exists; then
    return
  fi

  keycloak_import_if_missing module.tenant_identity.keycloak_realm.tenant "${TF_VAR_tenant_slug}"

  for group_key_and_name in \
    owner:workspace-owners \
    admin:workspace-admins \
    member:workspace-members \
    guest:workspace-guests; do
    local group_key="${group_key_and_name%%:*}"
    local group_name="${group_key_and_name#*:}"
    uuid="$(keycloak_lookup_group_id "${group_name}")"
    keycloak_import_if_missing "module.tenant_identity.keycloak_group.weave_product_role[\"${group_key}\"]" "${TF_VAR_tenant_slug}/${uuid}"
  done

  for group_key_and_name in \
    board_editors:weave-board-editors \
    calendar_editors:weave-calendar-editors \
    document_editors:weave-document-editors \
    meeting_hosts:weave-meeting-hosts \
    decision_records:weave-decision-recorders \
    weaver_pilot:weave-weaver-pilot \
    weaver_runtime:weave-weaver-runtime \
    weaver_group:weaver-group; do
    local group_key="${group_key_and_name%%:*}"
    local group_name="${group_key_and_name#*:}"
    uuid="$(keycloak_lookup_group_id "${group_name}")"
    keycloak_import_if_missing "module.tenant_identity.keycloak_group.weave_capability[\"${group_key}\"]" "${TF_VAR_tenant_slug}/${uuid}"
  done

  for client_key_and_id in \
    weave_app:weave-app \
    weave_backend:weave-backend \
    weave_identity_admin:weave-identity-admin \
    weave_admin_console:weave-admin-console \
    matrix_mas:matrix-mas \
    nextcloud:nextcloud; do
    local client_key="${client_key_and_id%%:*}"
    local client_id="${client_key_and_id#*:}"
    uuid="$(keycloak_lookup_client_uuid "${client_id}")"
    if [[ -n "${uuid}" ]]; then
      keycloak_import_if_missing "module.tenant_identity.keycloak_openid_client.client[\"${client_key}\"]" "${TF_VAR_tenant_slug}/${uuid}"
    fi
  done

  client_scope_id="$(keycloak_lookup_client_scope_id 'weave:workspace')"
  keycloak_import_if_missing module.tenant_identity.keycloak_openid_client_scope.weave_workspace "${TF_VAR_tenant_slug}/${client_scope_id}"

  for mapper_address_and_name in \
    module.tenant_identity.keycloak_openid_hardcoded_claim_protocol_mapper.weave_tenant_id:weave-tenant-id \
    module.tenant_identity.keycloak_openid_hardcoded_claim_protocol_mapper.weave_organization_name:weave-organization-name \
    module.tenant_identity.keycloak_openid_audience_protocol_mapper.weave_backend_audience:weave-app-audience \
    module.tenant_identity.keycloak_openid_audience_protocol_mapper.nextcloud_bearer_audience:nextcloud-bearer-audience; do
    local mapper_address="${mapper_address_and_name%%:*}"
    local mapper_name="${mapper_address_and_name#*:}"
    mapper_id="$(keycloak_lookup_client_scope_mapper_id "${client_scope_id}" "${mapper_name}")"
    keycloak_import_if_missing "${mapper_address}" "${TF_VAR_tenant_slug}/client-scope/${client_scope_id}/${mapper_id}"
  done

  for client_key_and_mapper in \
    weave_app:module.tenant_identity.keycloak_openid_group_membership_protocol_mapper.weave_app_groups \
    weave_admin_console:module.tenant_identity.keycloak_openid_group_membership_protocol_mapper.weave_admin_console_groups \
    nextcloud:module.tenant_identity.keycloak_openid_group_membership_protocol_mapper.nextcloud_groups; do
    local client_key="${client_key_and_mapper%%:*}"
    local mapper_address="${client_key_and_mapper#*:}"
    case "${client_key}" in
      weave_app) uuid="$(keycloak_lookup_client_uuid 'weave-app')" ;;
      weave_admin_console) uuid="$(keycloak_lookup_client_uuid 'weave-admin-console')" ;;
      nextcloud) uuid="$(keycloak_lookup_client_uuid 'nextcloud')" ;;
      *) fail "Unsupported Keycloak group mapper client key ${client_key}" ;;
    esac
    mapper_id="$(keycloak_lookup_client_mapper_id "${uuid}" groups)"
    keycloak_import_if_missing "${mapper_address}" "${TF_VAR_tenant_slug}/client/${uuid}/${mapper_id}"
  done

  if create_test_user_enabled; then
    uuid="$(keycloak_lookup_user_id test)"
    keycloak_import_if_missing 'module.tenant_identity.keycloak_user.test[0]' "${TF_VAR_tenant_slug}/${uuid}"
  fi
}

wait_for_nextcloud() {
  local attempts="${1:-120}"
  local sleep_seconds="${2:-5}"

  for ((i = 1; i <= attempts; i++)); do
    if docker exec --user www-data weave-nextcloud php occ status --output=json >/dev/null 2>&1; then
      return 0
    fi
    sleep "${sleep_seconds}"
  done

  fail "Nextcloud did not finish bootstrapping in time."
}

maybe_use_reverse_proxy_container_route() {
  local proxy_ip=""

  case "${WEAVE_RUNNER_HYGIENE:-false}" in
    true | TRUE | True | 1) ;;
    *) return 0 ;;
  esac

  proxy_ip="$(docker inspect -f '{{json .NetworkSettings.Networks}}' weave-proxy 2>/dev/null | python3 -c 'import json,sys
try:
    networks=json.load(sys.stdin)
except Exception:
    networks={}
for network in networks.values():
    ip=str(network.get("IPAddress", ""))
    if ip.count(".") == 3:
        print(ip)
        break
' || true)"
  if [[ -z "${proxy_ip}" ]]; then
    log "Reverse proxy route diagnostics: containers=$(docker ps --format '{{.Names}}' 2>/dev/null | grep '^weave-' | paste -sd, - || true) networks=$(docker network ls --format '{{.Name}}' 2>/dev/null | grep '^weave' | paste -sd, - || true) proxy_networks=$(docker inspect -f '{{json .NetworkSettings.Networks}}' weave-proxy 2>/dev/null || true)"
    fail "Reverse proxy container IP could not be resolved for local public Weave hostnames."
  fi

  LOOPBACK_RESOLVE_HOST="${proxy_ip}"
  PUBLIC_PROXY_PORT="443"
  export WEAVE_LOOPBACK_RESOLVE_HOST="${proxy_ip}"
  export WEAVE_PUBLIC_PROXY_PORT="${PUBLIC_PROXY_PORT}"
  log "Using reverse proxy container route for local public Weave hostnames."
}

occ() {
  docker exec --user www-data weave-nextcloud php occ "$@"
}

nextcloud_is_installed() {
  occ status --output=json 2>/dev/null | grep -q '"installed":true'
}

terraform_apply() {
  local dir="$1"
  local refresh="${WEAVE_IAC_REFRESH:-true}"

  "${WEAVE_IAC_BIN}" -chdir="${dir}" init -input=false
  "${WEAVE_IAC_BIN}" -chdir="${dir}" apply -refresh="${refresh}" -input=false -auto-approve
}

ensure_terraform_network_state() {
  local existing_network_id=""

  "${WEAVE_IAC_BIN}" -chdir="${INFRA_DIR}" init -input=false

  if "${WEAVE_IAC_BIN}" -chdir="${INFRA_DIR}" state show docker_network.weave_network >/dev/null 2>&1; then
    return
  fi

  if docker network inspect "${TF_VAR_docker_network_name}" >/dev/null 2>&1; then
    existing_network_id="$(docker network inspect --format '{{.ID}}' "${TF_VAR_docker_network_name}")"
    log "Importing existing Docker network ${TF_VAR_docker_network_name} into Terraform state..."
    "${WEAVE_IAC_BIN}" -chdir="${INFRA_DIR}" import -input=false docker_network.weave_network "${existing_network_id}"
  fi
}

terraform_state_has() {
  local address="$1"

  "${WEAVE_IAC_BIN}" -chdir="${INFRA_DIR}" state show "${address}" >/dev/null 2>&1
}

import_existing_docker_volume_state() {
  local address="$1"
  local name="$2"

  if terraform_state_has "${address}"; then
    return
  fi

  if docker volume inspect "${name}" >/dev/null 2>&1; then
    log "Importing existing Docker volume ${name} into OpenTofu state..."
    "${WEAVE_IAC_BIN}" -chdir="${INFRA_DIR}" import -input=false "${address}" "${name}"
  fi
}

import_existing_docker_container_state() {
  local address="$1"
  local name="$2"
  local container_id=""

  if terraform_state_has "${address}"; then
    return
  fi

  if docker container inspect "${name}" >/dev/null 2>&1; then
    container_id="$(docker container inspect --format '{{.ID}}' "${name}")"
    log "Importing existing Docker container ${name} into OpenTofu state..."
    "${WEAVE_IAC_BIN}" -chdir="${INFRA_DIR}" import -input=false "${address}" "${container_id}"
  fi
}

ensure_existing_stack_terraform_state() {
  "${WEAVE_IAC_BIN}" -chdir="${INFRA_DIR}" init -input=false

  import_existing_docker_volume_state module.postgres.docker_volume.data weave_db_data
  import_existing_docker_volume_state module.reverse_proxy.docker_volume.data weave_caddy_data
  import_existing_docker_volume_state module.reverse_proxy.docker_volume.config weave_caddy_config
  import_existing_docker_volume_state module.keycloak.docker_volume.data weave_keycloak_data
  import_existing_docker_volume_state module.nextcloud.docker_volume.data weave_nextcloud_data
  import_existing_docker_volume_state 'module.mailpit[0].docker_volume.data' "${TF_VAR_mailpit_volume_name:-weave_mailpit_data}"

  import_existing_docker_container_state module.postgres.docker_container.this weave-db
  import_existing_docker_container_state module.reverse_proxy.docker_container.this weave-proxy
  import_existing_docker_container_state module.keycloak.docker_container.this weave-keycloak
  import_existing_docker_container_state 'module.mailpit[0].docker_container.this' weave-mailpit
  import_existing_docker_container_state module.backend.docker_container.this weave-backend
  import_existing_docker_container_state module.mcp.docker_container.this weave-mcp-server
  import_existing_docker_container_state module.matrix.docker_container.mas weave-mas
  import_existing_docker_container_state module.matrix.docker_container.synapse weave-synapse
  import_existing_docker_container_state module.nextcloud.docker_container.this weave-nextcloud
}

terraform_output_raw() {
  local dir="$1"
  local name="$2"

  "${WEAVE_IAC_BIN}" -chdir="${dir}" output -raw "${name}"
}

refresh_runtime_container_if_image_changed() {
  local container_name="$1"
  local desired_image="$2"
  local runtime_label="$3"
  local desired_image_id
  local current_image_id

  if [[ -z "${desired_image}" ]]; then
    return
  fi

  if ! docker image inspect "${desired_image}" >/dev/null 2>&1; then
    return
  fi

  if ! docker container inspect "${container_name}" >/dev/null 2>&1; then
    log "Recreating missing ${runtime_label} container for image ${desired_image}..."
    "${WEAVE_IAC_BIN}" -chdir="${INFRA_DIR}" init -input=false
    "${WEAVE_IAC_BIN}" -chdir="${INFRA_DIR}" apply -input=false -auto-approve
    return
  fi

  desired_image_id="$(docker image inspect --format '{{.Id}}' "${desired_image}")"
  current_image_id="$(docker inspect --format '{{.Image}}' "${container_name}")"

  if [[ "${desired_image_id}" == "${current_image_id}" ]]; then
    return
  fi

  log "Refreshing ${runtime_label} container to match image ${desired_image}..."
  docker rm -f "${container_name}" >/dev/null
  "${WEAVE_IAC_BIN}" -chdir="${INFRA_DIR}" init -input=false
  "${WEAVE_IAC_BIN}" -chdir="${INFRA_DIR}" apply -input=false -auto-approve
}

refresh_runtime_containers_if_images_changed() {
  refresh_runtime_container_if_image_changed \
    weave-backend \
    "${TF_VAR_weave_backend_image:-}" \
    "Weave backend"
  refresh_runtime_container_if_image_changed \
    weave-mcp-server \
    "${TF_VAR_weave_mcp_server_image:-}" \
    "Weave MCP server"
}

ensure_postgres_bootstrap_applied() {
  local sql_file="${INFRA_DIR}/.generated/db/001-init.sql"

  log "Ensuring PostgreSQL bootstrap state is applied..."

  for _attempt in $(seq 1 30); do
    if docker exec weave-db pg_isready -U "${TF_VAR_db_admin_username}" -d postgres >/dev/null 2>&1; then
      docker exec -e PGPASSWORD="${TF_VAR_db_admin_password}" -i weave-db \
        psql -v ON_ERROR_STOP=1 -U "${TF_VAR_db_admin_username}" -d postgres < "${sql_file}"
      return 0
    fi
    sleep 2
  done

  fail "PostgreSQL bootstrap did not become ready in time for SQL initialization."
}

public_port_suffix() {
  local port="${PUBLIC_PROXY_PORT:-${TF_VAR_proxy_host_port}}"

  if [[ "${TF_VAR_public_scheme}" == "http" && "${port}" == "80" ]] ||
    [[ "${TF_VAR_public_scheme}" == "https" && "${port}" == "443" ]]; then
    printf ''
  else
    printf ':%s' "${port}"
  fi
}

public_host() {
  local subdomain="$1"
  printf '%s.%s' "${subdomain}" "${TF_VAR_tenant_domain}"
}

api_public_url() {
  printf '%s://%s%s' "${TF_VAR_public_scheme}" "$(public_host "${TF_VAR_api_subdomain}")" "$(public_port_suffix)"
}

auth_public_url() {
  printf '%s://%s%s' "${TF_VAR_public_scheme}" "$(public_host "${TF_VAR_auth_subdomain}")" "$(public_port_suffix)"
}

admin_public_url() {
  printf '%s://%s%s' "${TF_VAR_public_scheme}" "$(public_host "${TF_VAR_admin_subdomain}")" "$(public_port_suffix)"
}

product_public_url() {
  printf '%s://%s%s' "${TF_VAR_public_scheme}" "${TF_VAR_tenant_domain}" "$(public_port_suffix)"
}

client_public_url() {
  product_public_url
}

client_api_origin_url() {
  api_public_url
}

client_auth_public_url() {
  auth_public_url
}

client_matrix_facade_url() {
  client_api_origin_url
}

matrix_provider_public_url() {
  printf '%s://%s%s' "${TF_VAR_public_scheme}" "$(public_host "${TF_VAR_matrix_subdomain}")" "$(public_port_suffix)"
}

nextcloud_public_url() {
  printf '%s://%s%s' "${TF_VAR_public_scheme}" "$(public_host "${TF_VAR_nextcloud_subdomain}")" "$(public_port_suffix)"
}

host_port_from_url() {
  local url="$1"
  local host_port

  host_port="${url#*://}"
  host_port="${host_port%%/*}"
  if [[ "${host_port}" != *:* ]]; then
    case "${url%%://*}" in
      https) host_port="${host_port}:443" ;;
      http) host_port="${host_port}:80" ;;
    esac
  fi

  printf '%s\n' "${host_port}"
}

curl_nextcloud_actor_calendar_status() {
  local method="$1"
  local url="$2"
  local host_port
  local -a args=(--silent --show-error)

  host_port="$(host_port_from_url "${url}")"
  args+=(--resolve "${host_port}:${LOOPBACK_RESOLVE_HOST}")
  if [[ "${TF_VAR_public_scheme}" == "https" && -f "${TF_VAR_caddy_tls_ca_file}" ]]; then
    args+=(--cacert "${TF_VAR_caddy_tls_ca_file}")
  fi

  curl "${args[@]}" \
    --user "${TF_VAR_nextcloud_backend_actor_username}:${TF_VAR_nextcloud_backend_actor_token}" \
    --request "${method}" \
    --header 'Depth: 0' \
    -o /dev/null \
    -w '%{http_code}' \
    "${url}"
}

wait_for_public_http_200() {
  local name="$1"
  local url="$2"
  local attempts="${3:-60}"
  local sleep_seconds="${4:-2}"
  local host_port
  local status_code
  local -a args=(--silent --show-error)

  host_port="$(host_port_from_url "${url}")"
  args+=(--resolve "${host_port}:${LOOPBACK_RESOLVE_HOST}")
  if [[ "${TF_VAR_public_scheme}" == "https" && -f "${TF_VAR_caddy_tls_ca_file}" ]]; then
    args+=(--cacert "${TF_VAR_caddy_tls_ca_file}")
  fi

  for ((i = 1; i <= attempts; i++)); do
    status_code="$(curl "${args[@]}" -o /dev/null -w '%{http_code}' "${url}" || true)"
    if [[ "${status_code}" == "200" ]]; then
      return 0
    fi
    sleep "${sleep_seconds}"
  done

  fail "${name} never became ready at ${url}"
}

create_test_user_enabled() {
  case "${TF_VAR_create_test_user:-false}" in
    true | TRUE | True | 1)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

integration_test_base_url() {
  printf '%s/api' "$(client_api_origin_url)"
}

integration_test_oidc_issuer_url() {
  printf '%s/realms/%s' "$(client_auth_public_url)" "${TF_VAR_tenant_slug}"
}

write_app_config_summary() {
  local api_base_url
  local auth_base_url
  local matrix_url
  local nextcloud_url
  local product_url

  api_base_url="$(integration_test_base_url)"
  auth_base_url="$(client_auth_public_url)"
  matrix_url="$(client_matrix_facade_url)"
  nextcloud_url="$(nextcloud_public_url)"
  product_url="$(client_public_url)"

  mkdir -p "$(dirname -- "${APP_CONFIG_ENV_FILE}")"
  {
    printf '%s\n' '# Generated by weave-workspace/install.sh. Safe to share with local app/backend test runs; no secrets are included.'
    printf '%s\n' '# Product files/calendar routes are Weave product routes backed by backend facades.'
    printf '%s\n' '# Raw Nextcloud is a technical/admin/protocol fallback only, not the product files/calendar origin.'
    printf 'export WEAVE_PUBLIC_BASE_URL=%q\n' "${product_url}"
    printf 'export WEAVE_API_ORIGIN=%q\n' "$(client_api_origin_url)"
    printf 'export WEAVE_API_BASE_URL=%q\n' "${api_base_url}"
    printf 'export WEAVE_BASE_URL=%q\n' "${api_base_url}"
    printf 'export WEAVE_AUTH_BASE_URL=%q\n' "${auth_base_url}"
    printf 'export WEAVE_ADMIN_CONSOLE_URL=%q\n' "$(admin_public_url)"
    printf 'export WEAVE_ADMIN_CONSOLE_OIDC_CLIENT_ID=%q\n' 'weave-admin-console'
    printf 'export WEAVE_ORG_MANIFEST_URL=%q\n' "${api_base_url}/organization/manifest"
    printf 'export WEAVE_PROVIDER_PROFILE=%q\n' "${TF_VAR_provider_stack_profile}"
    printf 'export WEAVE_OIDC_ISSUER_URL=%q\n' "$(integration_test_oidc_issuer_url)"
    printf 'export WEAVE_OIDC_CLIENT_ID=%q\n' 'weave-app'
    printf 'export WEAVE_MATRIX_HOMESERVER_URL=%q\n' "${matrix_url}"
    printf 'export WEAVE_FILES_PRODUCT_URL=%q\n' "${product_url}/files"
    printf 'export WEAVE_CALENDAR_PRODUCT_URL=%q\n' "${product_url}/calendar"
    printf 'export WEAVE_LOCAL_CA_URL=%q\n' "http://${TF_VAR_tenant_domain}:${TF_VAR_proxy_http_host_port}/weave-local-ca.pem"
    printf 'export WEAVE_NEXTCLOUD_TECHNICAL_BASE_URL=%q\n' "${nextcloud_url}"
    printf 'export WEAVE_NEXTCLOUD_BASE_URL=%q\n' "${nextcloud_url}"
    printf 'export WEAVE_CALDAV_EXTERNAL_DISCOVERY_URL=%q\n' "${nextcloud_url}/remote.php/dav"
    printf 'export WEAVE_CALDAV_EXTERNAL_CREDENTIAL_MODE=%q\n' "nextcloud-login-flow-app-password"
    printf 'export WEAVE_CALDAV_EXTERNAL_PROFILE_PASSWORD_MODE=%q\n' "omit"
    printf 'export WEAVE_CALDAV_EXTERNAL_PRIVATE_USER_CALENDARS=%q\n' "disabled"
    printf 'export WEAVE_PROVIDER_STACK_PROFILE=%q\n' "${TF_VAR_provider_stack_profile}"
    printf 'export WEAVE_PROVIDER_STACK_READINESS=%q\n' "${TF_VAR_provider_stack_readiness}"
    printf 'export WEAVE_DEVOPS_PRIMARY_PROVIDER=%q\n' "${TF_VAR_devops_primary_provider}"
    printf 'export WEAVE_DEVOPS_GITLAB_RUNTIME_ENABLED=%q\n' "${TF_VAR_devops_gitlab_runtime_enabled}"
    printf 'export WEAVE_DEVOPS_GITLAB_BASE_URL=%q\n' "${TF_VAR_devops_gitlab_base_url}"
    printf 'export WEAVE_DEVOPS_GITLAB_WRITES_ENABLED=%q\n' "${TF_VAR_devops_gitlab_writes_enabled}"
    printf 'export WEAVE_OFFICE_PRIMARY_PROVIDER=%q\n' "${TF_VAR_office_primary_provider}"
    printf 'export WEAVE_OFFICE_ONLYOFFICE_RUNTIME_ENABLED=%q\n' "${TF_VAR_office_onlyoffice_runtime_enabled}"
    printf 'export WEAVE_OFFICE_ONLYOFFICE_DOCUMENT_SERVER_URL=%q\n' "${TF_VAR_office_onlyoffice_document_server_url}"
    printf 'export WEAVE_OFFICE_NEXTCLOUD_INTEGRATION_MODE=%q\n' "${TF_VAR_office_nextcloud_integration_mode}"
    printf 'export WEAVE_OFFICE_COLLABORA_RUNTIME_ENABLED=%q\n' "${TF_VAR_office_collabora_runtime_enabled}"
    printf 'export WEAVE_GROUPWARE_CONTACTS_RUNTIME_ENABLED=%q\n' "${TF_VAR_groupware_contacts_runtime_enabled}"
    printf 'export WEAVE_GROUPWARE_FORMS_RUNTIME_ENABLED=%q\n' "${TF_VAR_groupware_forms_runtime_enabled}"
    printf 'export WEAVE_LIVEKIT_ENABLED=%q\n' "${TF_VAR_livekit_runtime_enabled}"
    printf 'export WEAVE_LIVEKIT_TOKEN_ENDPOINT_CONFIGURED=%q\n' "$([[ -n "${TF_VAR_livekit_token_endpoint}" ]] && printf true || printf false)"
    printf 'export WEAVE_BOARDS_RUNTIME_ENABLED=%q\n' "${TF_VAR_boards_runtime_enabled}"
    printf 'export WEAVE_BOARDS_PROVIDER=%q\n' "${TF_VAR_boards_provider}"
    printf 'export WEAVE_BOARDS_OPENPROJECT_RUNTIME_ENABLED=%q\n' "${TF_VAR_boards_openproject_runtime_enabled}"
    printf 'export WEAVE_BOARDS_OPENPROJECT_READ_SYNC_ENABLED=%q\n' "${TF_VAR_boards_openproject_read_sync_enabled}"
    printf 'export WEAVE_BOARDS_OPENPROJECT_CONTEXT_AUTHORIZATION_ENABLED=%q\n' "${TF_VAR_boards_openproject_context_authorization_enabled}"
    printf 'export WEAVE_BOARDS_OPENPROJECT_AUDIT_CONSENT_ENABLED=%q\n' "${TF_VAR_boards_openproject_audit_consent_enabled}"
    printf 'export WEAVE_BOARDS_OPENPROJECT_PROVIDER_WRITES_ENABLED=%q\n' "${TF_VAR_boards_openproject_provider_writes_enabled}"
    printf 'export WEAVE_BOARDS_NEXTCLOUD_DECK_RUNTIME_ENABLED=%q\n' "${TF_VAR_boards_nextcloud_deck_runtime_enabled}"
    printf 'export WEAVE_BOARDS_OPENPROJECT_AUTH_MODE=%q\n' "${TF_VAR_boards_openproject_auth_mode}"
    printf 'export WEAVE_BOARDS_OPENPROJECT_BASE_URL=%q\n' "${TF_VAR_boards_openproject_base_url}"
    printf 'export WEAVE_TARGET_MOBILE=%q\n' "true"
    printf 'export WEAVE_TARGET_DESKTOP=%q\n' "true"
    printf 'export WEAVE_TARGET_WEB=%q\n' "false"
    printf 'export WEAVE_MATRIX_FEDERATION=%q\n' "disabled"
    printf 'export WEAVE_CHAT_E2EE=%q\n' "active-architecture-gated"
  } > "${APP_CONFIG_ENV_FILE}"
  chmod 0644 "${APP_CONFIG_ENV_FILE}"
}

preflight_checks() {
  log "Running preflight checks..."

  if ! docker info >/dev/null 2>&1; then
    fail "Docker daemon is not reachable. Start Docker Desktop or Docker Engine, then rerun ./install.sh."
  fi

  local host
  local unresolved_hosts=()
  local hosts=(
    "${TF_VAR_tenant_domain}"
    "$(public_host "${TF_VAR_api_subdomain}")"
    "$(public_host "${TF_VAR_admin_subdomain}")"
    "$(public_host "${TF_VAR_auth_subdomain}")"
    "mail.${TF_VAR_tenant_domain}"
    "$(public_host "${TF_VAR_nextcloud_subdomain}")"
    "$(public_host "${TF_VAR_matrix_subdomain}")"
  )

  for host in "${hosts[@]}"; do
    if grep -Eq "(^|[[:space:]])${host//./[.]}([[:space:]]|$)" /etc/hosts 2>/dev/null; then
      continue
    fi
    if command -v getent >/dev/null 2>&1; then
      if command -v timeout >/dev/null 2>&1; then
        timeout 2s getent hosts "${host}" >/dev/null 2>&1 && continue
      else
        getent hosts "${host}" >/dev/null 2>&1 && continue
      fi
    fi
    if command -v dscacheutil >/dev/null 2>&1; then
      dscacheutil -q host -a name "${host}" >/dev/null 2>&1 && continue
    fi
    if command -v getent >/dev/null 2>&1 || command -v dscacheutil >/dev/null 2>&1; then
      unresolved_hosts+=("${host}")
    fi
  done

  if (( ${#unresolved_hosts[@]} > 0 )); then
    log "Preflight warning: these canonical hosts do not resolve yet: ${unresolved_hosts[*]}"
    log "Add this /etc/hosts line before opening browser/native-client URLs:"
    log "${LOOPBACK_HOST} ${hosts[*]}"
  fi

  if command -v lsof >/dev/null 2>&1; then
    local port
    local ports=(
      "${TF_VAR_proxy_http_host_port}"
      "${TF_VAR_proxy_host_port}"
      "${TF_VAR_keycloak_host_port}"
      "${TF_VAR_keycloak_management_host_port}"
      "${TF_VAR_mas_host_port}"
      "${TF_VAR_synapse_host_port}"
      "${TF_VAR_nextcloud_host_port}"
      "${TF_VAR_backend_host_port}"
    )

    for port in "${ports[@]}"; do
      if lsof -nP -iTCP:"${port}" -sTCP:LISTEN >/dev/null 2>&1; then
        log "Preflight note: TCP port ${port} is already listening. If this is an existing Weave rerun, this is expected; otherwise stop the conflicting service or choose another TF_VAR_*_host_port."
      fi
    done
  else
    log "Preflight note: lsof is not installed, so port-conflict detection was skipped."
  fi

  log "Preflight checks completed."
}

ensure_generated_directories() {
  mkdir -p \
    "${ROOT_DIR}/.generated" \
    "${INFRA_DIR}/.generated/db" \
    "${INFRA_DIR}/.generated/caddy/certs" \
    "${INFRA_DIR}/.generated/mas" \
    "${INFRA_DIR}/.generated/synapse"
}

maybe_prepare_runner_hygiene() {
  if [[ "${WEAVE_RUNNER_HYGIENE:-false}" != "true" ]]; then
    return
  fi

  if [[ ! -x "${TEARDOWN_SCRIPT}" && ! -f "${TEARDOWN_SCRIPT}" ]]; then
    fail "Expected teardown helper at ${TEARDOWN_SCRIPT}"
  fi

  log "Running shared-host hygiene cleanup before bootstrap..."
  WEAVE_REMOVE_VOLUMES="${WEAVE_REMOVE_VOLUMES:-false}" bash "${TEARDOWN_SCRIPT}"
}

cleanup_partial_weave_containers() {
  local name
  local state
  local removed_any=false
  local containers=(
    weave-proxy
    weave-db
    weave-keycloak
    weave-backend
    weave-mas
    weave-synapse
    weave-nextcloud
  )

  for name in "${containers[@]}"; do
    if ! docker container inspect "${name}" >/dev/null 2>&1; then
      continue
    fi

    state="$(docker inspect --format '{{.State.Status}}' "${name}" 2>/dev/null || true)"
    case "${state}" in
      created|dead|exited)
        log "Removing leftover ${state} container ${name} before bootstrap..."
        docker rm -f "${name}" >/dev/null
        removed_any=true
        ;;
    esac
  done

  if [[ "${removed_any}" == true ]]; then
    log "Removed stale partial Weave containers."
  fi
}

ensure_default_inputs() {
  local defaults=(
    "TF_VAR_docker_network_name=weave_network"
    "TF_VAR_tenant_slug=weave"
    "TF_VAR_tenant_domain=weave.test"
    "TF_VAR_local_lan_host="
    "TF_VAR_auth_subdomain=auth"
    "TF_VAR_api_subdomain=api"
    "TF_VAR_admin_subdomain=admin"
    "TF_VAR_matrix_subdomain=matrix"
    "TF_VAR_nextcloud_subdomain=files"
    "TF_VAR_public_scheme=https"
    "TF_VAR_proxy_host_port=44443"
    "TF_VAR_proxy_http_host_port=44080"
    "TF_VAR_keycloak_host_port=48080"
    "TF_VAR_keycloak_admin_host=${LOOPBACK_HOST}"
    "TF_VAR_keycloak_management_host_port=49000"
    "TF_VAR_mas_host_port=48082"
    "TF_VAR_synapse_host_port=48008"
    "TF_VAR_nextcloud_host_port=48083"
    "TF_VAR_nextcloud_trusted_proxies=172.16.0.0/12"
    "TF_VAR_backend_host_port=48084"
    "TF_VAR_backend_container_port=8080"
    "TF_VAR_weave_backend_image=weave-backend:local"
    "TF_VAR_mcp_host_port=48085"
    "TF_VAR_mcp_container_port=8091"
    "TF_VAR_weave_mcp_server_image=weave-mcp-server:local"
    "TF_VAR_provider_stack_profile=fail-closed"
    "TF_VAR_provider_stack_readiness=fail-closed"
    "TF_VAR_devops_primary_provider=gitlab-ce-foss"
    "TF_VAR_devops_gitlab_runtime_enabled=false"
    "TF_VAR_devops_gitlab_base_url="
    "TF_VAR_devops_gitlab_writes_enabled=false"
    "TF_VAR_office_primary_provider=onlyoffice-community"
    "TF_VAR_office_onlyoffice_runtime_enabled=false"
    "TF_VAR_office_onlyoffice_document_server_url="
    "TF_VAR_office_nextcloud_integration_mode=nextcloud-onlyoffice-app-behind-backend-facade"
    "TF_VAR_office_collabora_runtime_enabled=false"
    "TF_VAR_groupware_contacts_runtime_enabled=false"
    "TF_VAR_groupware_forms_runtime_enabled=false"
    "TF_VAR_livekit_runtime_enabled=false"
    "TF_VAR_livekit_url="
    "TF_VAR_livekit_token_endpoint="
    "TF_VAR_livekit_image=livekit/livekit-server:v1.8"
    "TF_VAR_livekit_host_port=48091"
    "TF_VAR_livekit_rtc_tcp_host_port=48092"
    "TF_VAR_livekit_rtc_udp_host_port=48092"
    "TF_VAR_boards_runtime_enabled=false"
    "TF_VAR_boards_provider=local-workspace"
    "TF_VAR_boards_openproject_runtime_enabled=false"
    "TF_VAR_boards_openproject_read_sync_enabled=false"
    "TF_VAR_boards_openproject_context_authorization_enabled=false"
    "TF_VAR_boards_openproject_audit_consent_enabled=false"
    "TF_VAR_boards_openproject_provider_writes_enabled=false"
    "TF_VAR_boards_nextcloud_deck_runtime_enabled=false"
    "TF_VAR_boards_openproject_auth_mode=disabled"
    "TF_VAR_boards_openproject_base_url="
    "TF_VAR_context_authorization_tenant_claim=weave_tenant_id"
    "TF_VAR_context_authorization_tenant_fallback_claim=tenant_id"
    "TF_VAR_context_authorization_default_tenant_id=tenant-default"
    "TF_VAR_context_authorization_principal_claim=preferred_username"
    "TF_VAR_context_authorization_principal_ref_prefix=user:"
    "TF_VAR_context_authorization_bootstrap_context_id=workspace-default"
    "TF_VAR_context_authorization_bootstrap_principal_ref=user:test"
    "TF_VAR_context_authorization_dogfood_principal_ref=user:massimo"
    "TF_VAR_context_authorization_bootstrap_role=MEMBER"
    "TF_VAR_openproject_image=openproject/openproject:15"
    "TF_VAR_openproject_host_port=48086"
    "TF_VAR_synapse_uid=991"
    "TF_VAR_synapse_gid=991"
    "TF_VAR_db_name=weave"
    "TF_VAR_keycloak_admin_username=admin"
    "TF_VAR_db_admin_username=weave_admin"
    "TF_VAR_keycloak_db_username=keycloak"
    "TF_VAR_mas_db_username=mas"
    "TF_VAR_synapse_db_username=synapse"
    "TF_VAR_nextcloud_db_username=nextcloud"
    "TF_VAR_nextcloud_admin_username=admin"
    "TF_VAR_nextcloud_backend_actor_username=weave-backend"
  )

  local entry
  for entry in "${defaults[@]}"; do
    set_default_var "${entry%%=*}" "${entry#*=}"
  done

  # Sprint 32 local dogfood is DNS-first. A stale TF_VAR_local_lan_host from an
  # older LAN-IP run must not create a second public app/issuer/certificate
  # truth. Re-enable explicitly only if a future fallback profile is added.
  export TF_VAR_local_lan_host=""

  if create_test_user_enabled; then
    set_default_var TF_VAR_context_authorization_bootstrap_enabled true
  else
    set_default_var TF_VAR_context_authorization_bootstrap_enabled false
  fi

  set_default_var TF_VAR_caddy_tls_cert_file "${INFRA_DIR}/.generated/caddy/certs/weave.test.pem"
  set_default_var TF_VAR_caddy_tls_key_file "${INFRA_DIR}/.generated/caddy/certs/weave.test-key.pem"
  set_default_var TF_VAR_caddy_tls_ca_file "${INFRA_DIR}/.generated/caddy/certs/weave-local-ca.pem"
}

ensure_docker_provider_inputs() {
  if [[ -z "${TF_VAR_docker_host:-}" ]]; then
    export TF_VAR_docker_host
    TF_VAR_docker_host="$(detect_docker_host)"
  fi
}

ensure_generated_secrets() {
  set_default_secret TF_VAR_db_admin_password "$(random_base64 24)"
  set_default_secret TF_VAR_backend_db_password "$(random_base64 24)"
  set_default_secret TF_VAR_mcp_boundary_token "$(random_base64 32)"
  set_default_secret TF_VAR_keycloak_admin_password "$(random_base64 24)"
  set_default_secret TF_VAR_keycloak_db_password "$(random_base64 24)"
  set_default_secret TF_VAR_mas_db_password "$(random_base64 24)"
  set_default_secret TF_VAR_synapse_db_password "$(random_base64 24)"
  set_default_secret TF_VAR_nextcloud_db_password "$(random_base64 24)"
  set_default_secret TF_VAR_nextcloud_admin_password "$(random_base64 24)"
  set_default_secret TF_VAR_nextcloud_backend_actor_token "$(random_base64 24)"
  set_default_var TF_VAR_devops_gitlab_api_token ""
  set_default_var TF_VAR_office_onlyoffice_jwt_secret ""
  set_default_var TF_VAR_livekit_api_key ""
  set_default_var TF_VAR_livekit_api_secret ""
  set_default_var TF_VAR_boards_openproject_api_token ""
  set_default_var TF_VAR_openproject_secret_key_base ""
  set_default_secret TF_VAR_matrix_mas_client_secret "$(random_base64 32)"
  set_default_secret TF_VAR_identity_admin_client_secret "$(random_base64 32)"
  set_default_secret TF_VAR_identity_events_hmac_secret "$(random_base64 32)"
  set_default_secret TF_VAR_mas_encryption_secret "$(random_hex 32)"
  set_default_secret TF_VAR_mas_matrix_secret "$(random_base64 32)"
  set_default_secret TF_VAR_synapse_registration_shared_secret "$(random_base64 32)"
  set_default_secret TF_VAR_synapse_macaroon_secret_key "$(random_base64 32)"
  set_default_secret TF_VAR_synapse_form_secret "$(random_base64 32)"
  if create_test_user_enabled; then
    set_default_secret TF_VAR_test_user_password "$(random_base64 16)"
  fi
  ensure_mas_signing_key
  export TF_VAR_mas_signing_key_pem
}

certificate_alt_names() {
  local index=1
  local host
  local hosts=(
    "${TF_VAR_tenant_domain}"
    "*.${TF_VAR_tenant_domain}"
    "$(public_host "${TF_VAR_api_subdomain}")"
    "$(public_host "${TF_VAR_admin_subdomain}")"
    "$(public_host "${TF_VAR_auth_subdomain}")"
    "$(public_host "${TF_VAR_nextcloud_subdomain}")"
    "$(public_host "${TF_VAR_matrix_subdomain}")"
  )

  for host in "${hosts[@]}"; do
    printf 'DNS.%d = %s\n' "${index}" "${host}"
    index=$((index + 1))
  done

  if [[ -n "${TF_VAR_local_lan_host:-}" ]]; then
    if [[ "${TF_VAR_local_lan_host}" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
      printf 'IP.1 = %s\n' "${TF_VAR_local_lan_host}"
    else
      printf 'DNS.%d = %s\n' "${index}" "${TF_VAR_local_lan_host}"
    fi
  fi
}

ensure_local_tls_certificates() {
  local cert_file="${TF_VAR_caddy_tls_cert_file}"
  local key_file="${TF_VAR_caddy_tls_key_file}"
  local ca_file="${TF_VAR_caddy_tls_ca_file}"
  local cert_dir
  local key_dir
  local ca_dir
  local ca_key_file
  local csr_file
  local ext_file

  ca_key_file="${ca_file%.*}-key.pem"

  restore_default_local_tls_from_state

  if [[ -f "${cert_file}" && -f "${key_file}" && -f "${ca_file}" ]]; then
    local host
    local missing_hosts=()
    local required_hosts=(
      "${TF_VAR_tenant_domain}"
      "*.${TF_VAR_tenant_domain}"
      "$(public_host "${TF_VAR_api_subdomain}")"
      "$(public_host "${TF_VAR_admin_subdomain}")"
      "$(public_host "${TF_VAR_auth_subdomain}")"
      "$(public_host "${TF_VAR_nextcloud_subdomain}")"
      "$(public_host "${TF_VAR_matrix_subdomain}")"
    )
    if [[ -n "${TF_VAR_local_lan_host:-}" ]]; then
      required_hosts+=("${TF_VAR_local_lan_host}")
    fi

    for host in "${required_hosts[@]}"; do
      if [[ "${host}" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
        if ! openssl x509 -in "${cert_file}" -noout -checkip "${host}" 2>/dev/null | grep -q 'does match'; then
          missing_hosts+=("${host}")
        fi
      elif ! openssl x509 -in "${cert_file}" -noout -checkhost "${host}" 2>/dev/null | grep -q 'does match'; then
        missing_hosts+=("${host}")
      fi
    done

    if (( ${#missing_hosts[@]} == 0 )); then
      persist_default_local_tls_to_state
      return
    fi

    if [[ ! -f "${ca_key_file}" ]]; then
      fail "Existing local TLS certificate at ${cert_file} does not cover required hosts: ${missing_hosts[*]}. Provide a replacement cert/key that includes the canonical public hostnames, or restore ${ca_key_file} so install.sh can regenerate the leaf certificate."
    fi

    log "Regenerating local TLS leaf certificate to cover: ${missing_hosts[*]}"
    rm -f -- "${cert_file}" "${key_file}"
  fi

  if [[ -f "${cert_file}" || -f "${key_file}" ]] &&
    [[ ! -f "${cert_file}" || ! -f "${key_file}" || ! -f "${ca_file}" ]]; then
    fail "Local TLS cert, key, and CA files must all exist together. Check TF_VAR_caddy_tls_cert_file, TF_VAR_caddy_tls_key_file, and TF_VAR_caddy_tls_ca_file."
  fi

  cert_dir="$(dirname -- "${cert_file}")"
  key_dir="$(dirname -- "${key_file}")"
  ca_dir="$(dirname -- "${ca_file}")"

  if [[ "${cert_dir}" != "${key_dir}" && "${cert_dir}" != "${ca_dir}" ]]; then
    fail "Caddy TLS cert, key, and CA files must be in the same directory so the Docker cert mount contains all three files."
  fi

  mkdir -p "${cert_dir}" "${key_dir}" "${ca_dir}"

  if [[ -f "${ca_file}" && ! -f "${ca_key_file}" ]]; then
    fail "Existing local CA certificate found at ${ca_file}, but the CA private key is missing at ${ca_key_file}. Provide a matching leaf cert/key or restore the CA key."
  fi

  if [[ ! -f "${ca_file}" ]]; then
    openssl genrsa -out "${ca_key_file}" 4096
    chmod 600 "${ca_key_file}"
    openssl req -x509 -new -nodes \
      -key "${ca_key_file}" \
      -sha256 \
      -days 3650 \
      -out "${ca_file}" \
      -subj "/CN=Weave Local Development CA"
    chmod 644 "${ca_file}"
  fi

  csr_file="$(mktemp)"
  ext_file="$(mktemp)"

  openssl genrsa -out "${key_file}" 2048
  chmod 600 "${key_file}"
  openssl req -new \
    -key "${key_file}" \
    -out "${csr_file}" \
    -subj "/CN=$(public_host "${TF_VAR_auth_subdomain}")"

  {
    printf '%s\n' "authorityKeyIdentifier=keyid,issuer"
    printf '%s\n' "basicConstraints=CA:FALSE"
    printf '%s\n' "keyUsage = digitalSignature, keyEncipherment"
    printf '%s\n' "extendedKeyUsage = serverAuth"
    printf '%s\n' "subjectAltName = @alt_names"
    printf '%s\n' ""
    printf '%s\n' "[alt_names]"
    certificate_alt_names
  } > "${ext_file}"

  openssl x509 -req \
    -in "${csr_file}" \
    -CA "${ca_file}" \
    -CAkey "${ca_key_file}" \
    -CAcreateserial \
    -out "${cert_file}" \
    -days 825 \
    -sha256 \
    -extfile "${ext_file}"
  chmod 644 "${cert_file}"

  rm -f -- "${csr_file}" "${ext_file}"
  persist_default_local_tls_to_state
}

ensure_nextcloud_installed() {
  local nextcloud_database_name

  if nextcloud_is_installed; then
    return
  fi

  nextcloud_database_name="$(terraform_output_raw "${INFRA_DIR}" nextcloud_database_name)"

  occ maintenance:install \
    --database=pgsql \
    --database-host="weave-db" \
    --database-name="${nextcloud_database_name}" \
    --database-user="${TF_VAR_nextcloud_db_username}" \
    --database-pass="${TF_VAR_nextcloud_db_password}" \
    --admin-user="${TF_VAR_nextcloud_admin_username}" \
    --admin-pass="${TF_VAR_nextcloud_admin_password}"
}

configure_nextcloud_base_url() {
  local nextcloud_host
  local nextcloud_url

  nextcloud_host="$(public_host "${TF_VAR_nextcloud_subdomain}")"
  nextcloud_url="${TF_VAR_public_scheme}://${nextcloud_host}$(public_port_suffix)"

  occ config:system:set trusted_domains 0 --value="${nextcloud_host}"
  occ config:system:set trusted_domains 1 --value="localhost"
  occ config:system:set trusted_domains 2 --value="${LOOPBACK_HOST}"
  occ config:system:delete trusted_domains 3 >/dev/null 2>&1 || true
  occ config:system:set overwritehost --value="${nextcloud_host}$(public_port_suffix)"
  occ config:system:set overwrite.cli.url --value="${nextcloud_url}"
  occ config:system:set overwriteprotocol --value="${TF_VAR_public_scheme}"
}

install_nextcloud_tls_ca() {
  local ca_filename

  ca_filename="$(basename -- "${TF_VAR_caddy_tls_ca_file}")"
  docker exec --user 0 weave-nextcloud \
    install -m 0644 "/certs/${ca_filename}" "/usr/local/share/ca-certificates/weave-local-ca.crt"
  docker exec --user 0 weave-nextcloud update-ca-certificates
}

configure_nextcloud_oidc() {
  local issuer_url
  local nextcloud_client_id
  local nextcloud_client_secret
  local allow_insecure_http

  issuer_url="$(terraform_output_raw "${KEYCLOAK_DIR}" keycloak_issuer_url)"
  nextcloud_client_id="$(terraform_output_raw "${KEYCLOAK_DIR}" nextcloud_client_id)"
  nextcloud_client_secret="$(terraform_output_raw "${KEYCLOAK_DIR}" nextcloud_client_secret)"

  if ! occ app:enable user_oidc >/dev/null 2>&1; then
    occ app:install user_oidc
    occ app:enable user_oidc
  fi

  allow_insecure_http=0
  if [[ "${TF_VAR_public_scheme}" == "http" ]]; then
    allow_insecure_http=1
  fi

  # The OIDC provider is reached via the local reverse proxy hostname on the Docker network.
  # Nextcloud blocks RFC1918 / local-address targets by default, which breaks discovery in local dev.
  occ config:system:set allow_local_remote_servers --type=bool --value=true
  # user_oidc must validate bearer tokens for direct OCS/WebDAV access from
  # Weave clients. The system flag enables Nextcloud OIDC-provider validation
  # when available; the provider flags enable validation/provisioning for the
  # external Keycloak provider used by Weave's live stack.
  occ config:system:set user_oidc oidc_provider_bearer_validation --type=boolean --value=true
  occ config:app:set --type=boolean --value="${allow_insecure_http}" user_oidc allow_insecure_http
  occ user_oidc:provider keycloak \
    --clientid="${nextcloud_client_id}" \
    --clientsecret="${nextcloud_client_secret}" \
    --discoveryuri="${issuer_url}/.well-known/openid-configuration" \
    --group-provisioning=1 \
    --check-bearer=1 \
    --bearer-provisioning=1
}

nextcloud_backend_actor_exists() {
  occ user:info "${TF_VAR_nextcloud_backend_actor_username}" >/dev/null 2>&1
}

set_nextcloud_backend_actor_password() {
  docker exec \
    --user www-data \
    -e OC_PASS="${TF_VAR_nextcloud_backend_actor_token}" \
    weave-nextcloud \
    php occ user:resetpassword --password-from-env "${TF_VAR_nextcloud_backend_actor_username}" >/dev/null
}

create_nextcloud_backend_actor() {
  docker exec \
    --user www-data \
    -e OC_PASS="${TF_VAR_nextcloud_backend_actor_token}" \
    weave-nextcloud \
    php occ user:add \
      --password-from-env \
      --display-name="Weave Backend Service Account" \
      "${TF_VAR_nextcloud_backend_actor_username}" >/dev/null
}

ensure_nextcloud_backend_actor_calendar() {
  local calendar_id
  local calendar_url
  local create_output
  local create_status
  local read_status
  local -a calendar_ids=(
    personal
    weave-team-engineering
    weave-channel-engineering-general
  )

  for calendar_id in "${calendar_ids[@]}"; do
    create_output="$(occ dav:create-calendar "${TF_VAR_nextcloud_backend_actor_username}" "${calendar_id}" 2>&1)" && continue
    if printf '%s' "${create_output}" | grep -Eiq 'already exists|calendar.*exists|duplicate'; then
      continue
    fi
    if ! printf '%s' "${create_output}" | grep -Eiq 'not defined|unknown command|namespace .* not found'; then
      fail "Nextcloud backend actor calendar ${calendar_id} could not be created through occ: ${create_output}"
    fi

    calendar_url="$(nextcloud_public_url)/remote.php/dav/calendars/${TF_VAR_nextcloud_backend_actor_username}/${calendar_id}/"
    read_status="$(curl_nextcloud_actor_calendar_status PROPFIND "${calendar_url}" || true)"
    case "${read_status}" in
      200 | 207) continue ;;
      404) ;;
      *) fail "Nextcloud backend actor calendar ${calendar_id} is not readable before creation, HTTP ${read_status}" ;;
    esac

    create_status="$(curl_nextcloud_actor_calendar_status MKCALENDAR "${calendar_url}" || true)"
    case "${create_status}" in
      200 | 201 | 204) ;;
      *) fail "Nextcloud backend actor calendar ${calendar_id} could not be created through CalDAV, HTTP ${create_status}" ;;
    esac

    read_status="$(curl_nextcloud_actor_calendar_status PROPFIND "${calendar_url}" || true)"
    case "${read_status}" in
      200 | 207) ;;
      *) fail "Nextcloud backend actor calendar ${calendar_id} is not readable after creation, HTTP ${read_status}" ;;
    esac
  done
}

ensure_nextcloud_backend_actor() {
  [[ -n "${TF_VAR_nextcloud_backend_actor_username:-}" ]] || fail "TF_VAR_nextcloud_backend_actor_username must be set."
  [[ -n "${TF_VAR_nextcloud_backend_actor_token:-}" ]] || fail "TF_VAR_nextcloud_backend_actor_token must be set."

  if nextcloud_backend_actor_exists; then
    set_nextcloud_backend_actor_password
  else
    create_nextcloud_backend_actor
  fi

  ensure_nextcloud_backend_actor_calendar
}

print_summary() {
  local suffix
  local weave_client_id

  suffix="$(public_port_suffix)"
  weave_client_id="$(terraform_output_raw "${KEYCLOAK_DIR}" weave_app_client_id)"

  log
  log "Weave local/dev is ready."
  log
  log "Public URLs:"
  log "- App/Product: $(client_public_url)"
  log "- API:         $(integration_test_base_url)"
  log "- Auth:        $(client_auth_public_url)"
  log "- Files UX:    $(client_public_url)/files"
  log "- Calendar:    $(client_public_url)/calendar"
  log "- Matrix facade:   $(client_matrix_facade_url)"
  log "- Matrix provider: $(matrix_provider_public_url)  (southbound/operator path)"
  log "- Admin:      ${TF_VAR_public_scheme}://$(public_host "${TF_VAR_admin_subdomain}")${suffix}"
  log "- Files raw:  $(nextcloud_public_url)  (Nextcloud admin/protocol fallback, not normal end-user UX)"
  log "- Local CA:   http://${TF_VAR_tenant_domain}:${TF_VAR_proxy_http_host_port}/weave-local-ca.pem"
  log
  log "App config file (no secrets): ${APP_CONFIG_ENV_FILE}"
  log "DNS-first local hosts: ${TF_VAR_tenant_domain} $(public_host "${TF_VAR_api_subdomain}") $(public_host "${TF_VAR_auth_subdomain}") $(public_host "${TF_VAR_nextcloud_subdomain}") $(public_host "${TF_VAR_matrix_subdomain}") $(public_host "${TF_VAR_admin_subdomain}") mail.${TF_VAR_tenant_domain}"
  log "Trust this local TLS CA certificate before opening browser/native-client URLs: ${TF_VAR_caddy_tls_ca_file}"
  log
  log "MVP feature flags:"
  log "- Mobile:            enabled"
  log "- Desktop:           enabled"
  log "- Browser/Web:       later"
  log "- Matrix federation: disabled"
  log "- Chat E2EE:         active architecture, gated until encrypted-room/device validation"
  log
  log "Health checks:"
  log "- Backend ready: $(integration_test_base_url)/health/ready"
  log "- MCP ready: http://${LOOPBACK_HOST}:${TF_VAR_mcp_host_port}/actuator/health"
  log "- Keycloak discovery: $(integration_test_oidc_issuer_url)/.well-known/openid-configuration"
  log "- Matrix facade versions: $(client_matrix_facade_url)/_matrix/client/versions"
  log "- Matrix default rooms: #announcements:$(public_host "${TF_VAR_matrix_subdomain}"), #general:$(public_host "${TF_VAR_matrix_subdomain}"), #help:$(public_host "${TF_VAR_matrix_subdomain}")"
  log "- Raw Nextcloud: $(nextcloud_public_url)/"
  log "- Dogfood mail inbox: ${TF_VAR_public_scheme}://mail.${TF_VAR_tenant_domain}${suffix} (private LAN only; loopback fallback http://127.0.0.1:${TF_VAR_mailpit_web_host_port:-8025})"
  log
  log "Admin credentials (local/dev only):"
  log "- Keycloak admin user: ${TF_VAR_keycloak_admin_username} (password stored in ${BOOTSTRAP_ENV_FILE})"
  log "- Nextcloud admin user: ${TF_VAR_nextcloud_admin_username} (password stored in ${BOOTSTRAP_ENV_FILE})"
  log "- Nextcloud backend actor user: ${TF_VAR_nextcloud_backend_actor_username} (token stored in ${BOOTSTRAP_ENV_FILE})"
  log "- Weave app client ID: ${weave_client_id}"
  log
  log "Next steps:"
  log "- Open $(product_public_url) or launch the configured native client."
  log "- Run: TF_VAR_create_test_user=true ./install.sh && ./smoke-test.sh"
  log "- For diagnostics, run: ./operator-check.sh"
  log "- Weave backend image: ${TF_VAR_weave_backend_image}"
  log "- Weave MCP image: ${TF_VAR_weave_mcp_server_image}"

  if create_test_user_enabled; then
    log "- Test user: ${TEST_USER_EMAIL} (password stored in ${BOOTSTRAP_ENV_FILE})"
  fi
}

main() {
  require_command curl
  require_command docker
  require_command openssl
  require_command python3
  require_command "${WEAVE_IAC_BIN}"

  ensure_generated_directories
  load_persisted_env
  ensure_default_inputs
  preflight_checks
  maybe_prepare_runner_hygiene
  cleanup_partial_weave_containers
  ensure_docker_provider_inputs
  ensure_generated_secrets
  ensure_local_tls_certificates
  persist_bootstrap_env
  # shellcheck disable=SC1090
  source "${SYNAPSE_VOLUME_HELPER}"
  ensure_terraform_network_state
  synapse_reconcile_terraform_state
  ensure_existing_stack_terraform_state

  log "Applying infrastructure module..."
  terraform_apply "${INFRA_DIR}"
  maybe_use_reverse_proxy_container_route
  synapse_repair_volume_permissions
  synapse_verify_volume_writable
  ensure_postgres_bootstrap_applied
  refresh_runtime_containers_if_images_changed

  log "Waiting for Keycloak management readiness..."
  wait_for_http_200 "Keycloak management" "http://${LOOPBACK_HOST}:${TF_VAR_keycloak_management_host_port}/health/ready"

  log "Waiting for Keycloak admin login readiness..."
  wait_for_keycloak_admin_login 90 2
  ensure_existing_keycloak_terraform_state

  log "Applying Keycloak configuration module..."
  terraform_apply "${KEYCLOAK_DIR}"

  log "Waiting for Weave backend readiness..."
  wait_for_http_200 "Weave backend" "http://${LOOPBACK_HOST}:${TF_VAR_backend_host_port}/api/health/ready"

  log "Waiting for Weave MCP readiness..."
  wait_for_http_200 "Weave MCP server" "http://${LOOPBACK_HOST}:${TF_VAR_mcp_host_port}/actuator/health"

  log "Waiting for Matrix Authentication Service readiness..."
  wait_for_http_200 "Matrix Authentication Service" "http://${LOOPBACK_HOST}:${TF_VAR_mas_host_port}/health"

  log "Waiting for Synapse readiness..."
  wait_for_http_200 "Synapse" "http://${LOOPBACK_HOST}:${TF_VAR_synapse_host_port}/_matrix/client/versions"

  log "Provisioning default Matrix workspace space and rooms..."
  bash "${ROOT_DIR}/provision-matrix-default-workspace.sh"

  log "Waiting for Nextcloud OCC availability..."
  wait_for_nextcloud 120 5

  log "Installing and configuring Nextcloud..."
  ensure_nextcloud_installed
  install_nextcloud_tls_ca
  configure_nextcloud_base_url

  log "Configuring Nextcloud OIDC provider..."
  configure_nextcloud_oidc

  log "Ensuring backend-owned Nextcloud actor for files/calendar facades..."
  ensure_nextcloud_backend_actor

  print_summary
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
