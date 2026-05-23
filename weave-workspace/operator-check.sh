#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
BOOTSTRAP_ENV_FILE="${ROOT_DIR}/.generated/bootstrap.env"
APP_CONFIG_ENV_FILE="${ROOT_DIR}/.generated/app-config.env"
SYNAPSE_VOLUME_HELPER="${ROOT_DIR}/lib/synapse-volume.sh"

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

load_bootstrap_env() {
  if [[ -f "${BOOTSTRAP_ENV_FILE}" ]]; then
    # shellcheck disable=SC1090
    source "${BOOTSTRAP_ENV_FILE}"
  fi
}

public_port_suffix() {
  local scheme="${TF_VAR_public_scheme:-https}"
  local port="${TF_VAR_proxy_host_port:-443}"

  if [[ "${scheme}" == "http" && "${port}" == "80" ]] || [[ "${scheme}" == "https" && "${port}" == "443" ]]; then
    printf ''
    return
  fi

  printf ':%s' "${port}"
}

public_url() {
  local subdomain="$1"
  printf '%s://%s.%s%s' \
    "${TF_VAR_public_scheme:-https}" \
    "${subdomain}" \
    "${TF_VAR_tenant_domain:-weave.local}" \
    "$(public_port_suffix)"
}

product_public_url() {
  printf '%s://%s%s' \
    "${TF_VAR_public_scheme:-https}" \
    "${TF_VAR_tenant_domain:-weave.local}" \
    "$(public_port_suffix)"
}

api_public_url() {
  public_url "${TF_VAR_api_subdomain:-api}"
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

curl_common_args() {
  local url="$1"
  local host_port
  local -a args=(--silent --show-error --fail)

  host_port="$(host_port_from_url "${url}")"
  args+=(--resolve "${host_port}:127.0.0.1")

  if [[ -n "${WEAVE_TLS_CA_FILE:-}" ]]; then
    args+=(--cacert "${WEAVE_TLS_CA_FILE}")
  elif [[ -n "${TF_VAR_caddy_tls_ca_file:-}" && -f "${TF_VAR_caddy_tls_ca_file}" ]]; then
    args+=(--cacert "${TF_VAR_caddy_tls_ca_file}")
  fi

  printf '%s\0' "${args[@]}"
}

curl_json() {
  local url="$1"
  local -a args=()

  while IFS= read -r -d '' arg; do
    args+=("${arg}")
  done < <(curl_common_args "${url}")

  curl "${args[@]}" "$url"
}

curl_form() {
  local url="$1"
  shift
  local -a args=()

  while IFS= read -r -d '' arg; do
    args+=("${arg}")
  done < <(curl_common_args "${url}")

  curl "${args[@]}" \
    --header 'content-type: application/x-www-form-urlencoded' \
    "$url" "$@"
}

curl_auth_json() {
  local token="$1"
  local url="$2"
  local -a args=()

  while IFS= read -r -d '' arg; do
    args+=("${arg}")
  done < <(curl_common_args "${url}")

  curl "${args[@]}" \
    --header "Authorization: Bearer ${token}" \
    "$url"
}

url_encode() {
  local value="$1"
  jq -nr --arg value "${value}" '$value|@uri'
}

matrix_room_id_by_alias() {
  local matrix_base_url="$1"
  local alias="$2"
  local response

  response="$(curl_json "${matrix_base_url}/_matrix/client/v3/directory/room/$(url_encode "${alias}")")"
  jq -r '.room_id' <<<"${response}"
}

curl_status() {
  local url="$1"
  local host_port
  local -a args=(--silent --show-error)

  host_port="$(host_port_from_url "${url}")"
  args+=(--resolve "${host_port}:127.0.0.1")

  if [[ -n "${WEAVE_TLS_CA_FILE:-}" ]]; then
    args+=(--cacert "${WEAVE_TLS_CA_FILE}")
  elif [[ -n "${TF_VAR_caddy_tls_ca_file:-}" && -f "${TF_VAR_caddy_tls_ca_file}" ]]; then
    args+=(--cacert "${TF_VAR_caddy_tls_ca_file}")
  fi

  curl "${args[@]}" -o /dev/null -w '%{http_code}' "$url"
}

curl_basic_propfind_status() {
  local username="$1"
  local password="$2"
  local url="$3"
  local -a args=()

  while IFS= read -r -d '' arg; do
    args+=("${arg}")
  done < <(curl_common_args "${url}")

  curl "${args[@]}" \
    --user "${username}:${password}" \
    --request PROPFIND \
    --header 'Depth: 0' \
    -o /dev/null \
    -w '%{http_code}' \
    "$url"
}

curl_auth_status() {
  local token="$1"
  local url="$2"
  local host_port
  local -a args=(--silent --show-error)

  host_port="$(host_port_from_url "${url}")"
  args+=(--resolve "${host_port}:127.0.0.1")

  if [[ -n "${WEAVE_TLS_CA_FILE:-}" ]]; then
    args+=(--cacert "${WEAVE_TLS_CA_FILE}")
  elif [[ -n "${TF_VAR_caddy_tls_ca_file:-}" && -f "${TF_VAR_caddy_tls_ca_file}" ]]; then
    args+=(--cacert "${TF_VAR_caddy_tls_ca_file}")
  fi

  curl "${args[@]}" -H "Authorization: Bearer ${token}" -o /dev/null -w '%{http_code}' "$url"
}

assert_container_running() {
  local name="$1"
  local state

  state="$(docker inspect --format '{{.State.Status}}' "${name}" 2>/dev/null || true)"
  [[ "${state}" == "running" ]] || fail "Operator check failed: container ${name} is not running"
}

assert_http_200() {
  local name="$1"
  local url="$2"
  local status

  status="$(curl --silent --show-error -o /dev/null -w '%{http_code}' "$url" || true)"
  [[ "${status}" == "200" ]] || fail "Operator check failed: ${name} returned HTTP ${status} at ${url}"
}

assert_json() {
  local json="$1"
  local jq_filter="$2"
  local description="$3"

  jq -e "${jq_filter}" >/dev/null <<<"${json}" || fail "Operator check failed: ${description}"
}

container_env_value() {
  local container="$1"
  local name="$2"

  docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "${container}" 2>/dev/null |
    awk -v name="${name}" 'index($0, name "=") == 1 { print substr($0, length(name) + 2); found = 1 } END { if (!found) exit 1 }'
}

container_env_count() {
  local container="$1"
  local name="$2"

  docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "${container}" 2>/dev/null |
    awk -v name="${name}" 'index($0, name "=") == 1 { count += 1 } END { print count + 0 }'
}

assert_nextcloud_backend_actor_calendar() {
  local actor_username="$1"
  local actor_token="$2"
  local calendar_id="$3"
  local calendar_url
  local status

  calendar_url="$(public_url "${TF_VAR_nextcloud_subdomain:-files}")/remote.php/dav/calendars/${actor_username}/${calendar_id}/"
  status="$(curl_basic_propfind_status "${actor_username}" "${actor_token}" "${calendar_url}" || true)"
  case "${status}" in
    200|207) ;;
    *) fail "Operator check failed: Nextcloud backend actor calendar ${calendar_id} is not provisioned or not readable through CalDAV, HTTP ${status}" ;;
  esac
}

assert_backend_env_present() {
  local name="$1"
  local value

  value="$(container_env_value weave-backend "${name}" || true)"
  [[ -n "${value}" ]] || fail "Operator check failed: weave-backend is missing required ${name} Nextcloud facade configuration"
}

assert_backend_provider_stack_config() {
  local name
  local gitlab_enabled
  local forgejo_enabled
  local onlyoffice_enabled
  local contacts_enabled
  local forms_enabled
  local deck_enabled

  log "Checking provider-stack runtime fail-closed configuration..."
  for name in \
    WEAVE_PROVIDER_STACK_PROFILE \
    WEAVE_PROVIDER_STACK_READINESS \
    WEAVE_DEVOPS_PRIMARY_PROVIDER \
    WEAVE_DEVOPS_ALTERNATIVE_PROVIDER \
    WEAVE_DEVOPS_GITLAB_RUNTIME_ENABLED \
    WEAVE_DEVOPS_GITLAB_BASE_URL \
    WEAVE_DEVOPS_GITLAB_API_TOKEN \
    WEAVE_DEVOPS_GITLAB_WRITES_ENABLED \
    WEAVE_DEVOPS_FORGEJO_RUNTIME_ENABLED \
    WEAVE_DEVOPS_FORGEJO_BASE_URL \
    WEAVE_DEVOPS_FORGEJO_API_TOKEN \
    WEAVE_DEVOPS_FORGEJO_WRITES_ENABLED \
    WEAVE_OFFICE_PRIMARY_PROVIDER \
    WEAVE_OFFICE_ONLYOFFICE_RUNTIME_ENABLED \
    WEAVE_OFFICE_ONLYOFFICE_DOCUMENT_SERVER_URL \
    WEAVE_OFFICE_ONLYOFFICE_JWT_SECRET \
    WEAVE_OFFICE_NEXTCLOUD_INTEGRATION_MODE \
    WEAVE_OFFICE_COLLABORA_RUNTIME_ENABLED \
    WEAVE_GROUPWARE_CONTACTS_RUNTIME_ENABLED \
    WEAVE_GROUPWARE_FORMS_RUNTIME_ENABLED \
    WEAVE_BOARDS_NEXTCLOUD_DECK_RUNTIME_ENABLED; do
    [[ "$(container_env_count weave-backend "${name}")" == "1" ]] || fail "Operator check failed: weave-backend must define ${name} exactly once"
  done

  [[ "$(container_env_value weave-backend WEAVE_PROVIDER_STACK_PROFILE)" == "fail-closed" || "$(container_env_value weave-backend WEAVE_PROVIDER_STACK_PROFILE)" == "local-live" ]] || \
    fail "Operator check failed: unsupported provider-stack profile"
  [[ "$(container_env_value weave-backend WEAVE_DEVOPS_PRIMARY_PROVIDER)" == "gitlab-ce-foss" ]] || \
    fail "Operator check failed: GitLab CE/FOSS must remain the primary DevOps provider assumption"
  [[ "$(container_env_value weave-backend WEAVE_DEVOPS_ALTERNATIVE_PROVIDER)" == "forgejo" ]] || \
    fail "Operator check failed: Forgejo must remain the first-class DevOps alternative"
  [[ "$(container_env_value weave-backend WEAVE_OFFICE_PRIMARY_PROVIDER)" == "onlyoffice-community" ]] || \
    fail "Operator check failed: ONLYOFFICE Docs Community must remain the default Office candidate"
  [[ "$(container_env_value weave-backend WEAVE_OFFICE_NEXTCLOUD_INTEGRATION_MODE)" == "nextcloud-onlyoffice-app-behind-backend-facade" ]] || \
    fail "Operator check failed: Office integration must stay behind Nextcloud/backend facade"

  gitlab_enabled="$(container_env_value weave-backend WEAVE_DEVOPS_GITLAB_RUNTIME_ENABLED)"
  forgejo_enabled="$(container_env_value weave-backend WEAVE_DEVOPS_FORGEJO_RUNTIME_ENABLED)"
  onlyoffice_enabled="$(container_env_value weave-backend WEAVE_OFFICE_ONLYOFFICE_RUNTIME_ENABLED)"
  contacts_enabled="$(container_env_value weave-backend WEAVE_GROUPWARE_CONTACTS_RUNTIME_ENABLED)"
  forms_enabled="$(container_env_value weave-backend WEAVE_GROUPWARE_FORMS_RUNTIME_ENABLED)"
  deck_enabled="$(container_env_value weave-backend WEAVE_BOARDS_NEXTCLOUD_DECK_RUNTIME_ENABLED)"

  [[ "$(container_env_value weave-backend WEAVE_DEVOPS_GITLAB_WRITES_ENABLED)" != "true" ]] || fail "Operator check failed: GitLab provider writes must stay disabled for the read-only DevOps facade"
  [[ "$(container_env_value weave-backend WEAVE_DEVOPS_FORGEJO_WRITES_ENABLED)" != "true" ]] || fail "Operator check failed: Forgejo provider writes must stay disabled for the read-only DevOps facade"
  [[ "$(container_env_value weave-backend WEAVE_OFFICE_COLLABORA_RUNTIME_ENABLED)" != "true" ]] || fail "Operator check failed: Collabora/CODE must stay disabled until licensing/runtime fit is validated"
  [[ "${contacts_enabled}" != "true" ]] || fail "Operator check failed: Contacts runtime must stay disabled until backend PR #104 is merged and validated"
  [[ "${forms_enabled}" != "true" ]] || fail "Operator check failed: Forms runtime must stay disabled until backend PR #104 is merged and validated"
  [[ "${deck_enabled}" != "true" ]] || fail "Operator check failed: Nextcloud Deck must stay disabled while OpenProject is the primary Boards provider assumption"

  if [[ "${gitlab_enabled}" != "true" ]]; then
    [[ -z "$(container_env_value weave-backend WEAVE_DEVOPS_GITLAB_API_TOKEN)" ]] || fail "Operator check failed: disabled GitLab runtime must not carry an API token"
  else
    [[ -n "$(container_env_value weave-backend WEAVE_DEVOPS_GITLAB_BASE_URL)" ]] || fail "Operator check failed: enabled GitLab runtime requires a backend-only base URL"
    [[ -n "$(container_env_value weave-backend WEAVE_DEVOPS_GITLAB_API_TOKEN)" ]] || fail "Operator check failed: enabled GitLab runtime requires a backend-held service token"
  fi

  if [[ "${forgejo_enabled}" != "true" ]]; then
    [[ -z "$(container_env_value weave-backend WEAVE_DEVOPS_FORGEJO_API_TOKEN)" ]] || fail "Operator check failed: disabled Forgejo runtime must not carry an API token"
  else
    [[ -n "$(container_env_value weave-backend WEAVE_DEVOPS_FORGEJO_BASE_URL)" ]] || fail "Operator check failed: enabled Forgejo runtime requires a backend-only base URL"
    [[ -n "$(container_env_value weave-backend WEAVE_DEVOPS_FORGEJO_API_TOKEN)" ]] || fail "Operator check failed: enabled Forgejo runtime requires a backend-held service token"
  fi

  if [[ "${onlyoffice_enabled}" != "true" ]]; then
    [[ -z "$(container_env_value weave-backend WEAVE_OFFICE_ONLYOFFICE_JWT_SECRET)" ]] || fail "Operator check failed: disabled ONLYOFFICE runtime must not carry a JWT secret"
  else
    [[ -n "$(container_env_value weave-backend WEAVE_OFFICE_ONLYOFFICE_DOCUMENT_SERVER_URL)" ]] || fail "Operator check failed: enabled ONLYOFFICE runtime requires a backend-only Document Server URL"
    [[ -n "$(container_env_value weave-backend WEAVE_OFFICE_ONLYOFFICE_JWT_SECRET)" ]] || fail "Operator check failed: enabled ONLYOFFICE runtime requires a backend-held JWT secret"
  fi

  if [[ -f "${APP_CONFIG_ENV_FILE}" ]]; then
    ! grep -Eq 'WEAVE_DEVOPS_.*API_TOKEN|WEAVE_OFFICE_ONLYOFFICE_JWT_SECRET|TF_VAR_devops_.*api_token|TF_VAR_office_onlyoffice_jwt_secret' "${APP_CONFIG_ENV_FILE}" || \
      fail "Operator check failed: no-secret app config exposes provider stack secrets"
  fi
}

assert_backend_boards_openproject_config() {
  local preview_provider
  local runtime_enabled
  local read_sync_enabled
  local auth_mode
  local base_url
  local api_token
  local provider_writes_enabled

  log "Checking backend Boards/OpenProject runtime gates..."
  preview_provider="$(container_env_value weave-backend WEAVE_BOARDS_PREVIEW_PROVIDER || true)"
  runtime_enabled="$(container_env_value weave-backend WEAVE_BOARDS_OPENPROJECT_RUNTIME_ENABLED || true)"
  read_sync_enabled="$(container_env_value weave-backend WEAVE_BOARDS_OPENPROJECT_READ_SYNC_ENABLED || true)"
  auth_mode="$(container_env_value weave-backend WEAVE_BOARDS_OPENPROJECT_AUTH_MODE || true)"
  base_url="$(container_env_value weave-backend WEAVE_BOARDS_OPENPROJECT_BASE_URL || true)"
  api_token="$(container_env_value weave-backend WEAVE_BOARDS_OPENPROJECT_API_TOKEN || true)"
  provider_writes_enabled="$(container_env_value weave-backend WEAVE_BOARDS_OPENPROJECT_PROVIDER_WRITES_ENABLED || true)"

  [[ -n "${preview_provider}" ]] || fail "Operator check failed: weave-backend is missing WEAVE_BOARDS_PREVIEW_PROVIDER"
  [[ "${preview_provider}" == "local-preview" || "${preview_provider}" == "openproject" ]] || fail "Operator check failed: unsupported boards preview provider ${preview_provider}"
  [[ "${provider_writes_enabled}" != "true" ]] || fail "Operator check failed: OpenProject provider writes must stay disabled for the read-only MVP/runtime path"

  if [[ "${preview_provider}" != "openproject" && "${runtime_enabled}" != "true" && "${read_sync_enabled}" != "true" ]]; then
    [[ "${auth_mode}" == "disabled" ]] || fail "Operator check failed: disabled OpenProject runtime must use auth-mode=disabled"
    [[ -z "${api_token}" ]] || fail "Operator check failed: disabled OpenProject runtime must not carry a provider API token"
    return
  fi

  [[ "${preview_provider}" == "openproject" ]] || fail "Operator check failed: OpenProject runtime requires WEAVE_BOARDS_PREVIEW_PROVIDER=openproject"
  [[ "${runtime_enabled}" == "true" ]] || fail "Operator check failed: OpenProject runtime requires WEAVE_BOARDS_OPENPROJECT_RUNTIME_ENABLED=true"
  [[ "${read_sync_enabled}" == "true" ]] || fail "Operator check failed: OpenProject read-sync requires WEAVE_BOARDS_OPENPROJECT_READ_SYNC_ENABLED=true"
  [[ "$(container_env_value weave-backend WEAVE_BOARDS_OPENPROJECT_CONTEXT_AUTHORIZATION_ENABLED || true)" == "true" ]] || fail "Operator check failed: OpenProject read-sync requires Context/Space authorization gate enabled"
  [[ "${auth_mode}" == "service-token" ]] || fail "Operator check failed: OpenProject read-sync requires backend-held service-token auth"
  [[ -n "${base_url}" ]] || fail "Operator check failed: OpenProject read-sync requires a backend-only base URL"
  [[ -n "${api_token}" ]] || fail "Operator check failed: OpenProject read-sync requires a backend-held API token"
}

assert_matrix_room_unencrypted_until_e2ee_promoted() {
  local room_name="$1"
  local room_id="$2"
  local status

  [[ -n "${WEAVE_MATRIX_PROVISIONER_ACCESS_TOKEN:-}" ]] ||     fail "Operator check failed: Matrix provisioner token is missing from private bootstrap env; cannot verify E2EE room posture"

  status="$(curl_auth_status "${WEAVE_MATRIX_PROVISIONER_ACCESS_TOKEN}" "${WEAVE_MATRIX_HOMESERVER_URL}/_matrix/client/v3/rooms/$(url_encode "${room_id}")/state/m.room.encryption/" || true)"
  case "${status}" in
    404) ;;
    200) fail "Operator check failed: ${room_name} has m.room.encryption while WEAVE_CHAT_E2EE is still active-architecture-gated; promote encrypted-room/device/recovery validation before claiming E2EE" ;;
    *) fail "Operator check failed: could not verify Matrix encryption posture for ${room_name}, HTTP ${status}" ;;
  esac
}

check_matrix_provisioner_key_backup_diagnostic() {
  local status

  [[ -n "${WEAVE_MATRIX_PROVISIONER_ACCESS_TOKEN:-}" ]] ||     fail "Operator check failed: Matrix provisioner token is missing from private bootstrap env; cannot verify provisioner key-backup posture"

  status="$(curl_auth_status "${WEAVE_MATRIX_PROVISIONER_ACCESS_TOKEN}" "${WEAVE_MATRIX_HOMESERVER_URL}/_matrix/client/v3/room_keys/version" || true)"
  case "${status}" in
    404) ;;
    200) log "Matrix provisioner account has key-backup state; this is diagnostic only and does not prove global E2EE recovery readiness." ;;
    *) fail "Operator check failed: could not verify Matrix provisioner key-backup posture, HTTP ${status}" ;;
  esac
}

assert_backend_nextcloud_actor_config() {
  local actor_username
  local actor_token
  local actor_model
  local webdav_root
  local backend_nextcloud_base_url
  local caldav_base_url
  local caldav_template
  local caldav_auth_mode
  local caldav_username
  local name

  log "Checking backend-owned Nextcloud actor configuration..."
  for name in \
    WEAVE_NEXTCLOUD_BASE_URL \
    WEAVE_NEXTCLOUD_FILES_ACTOR_MODEL \
    WEAVE_NEXTCLOUD_FILES_ACTOR_USERNAME \
    WEAVE_NEXTCLOUD_FILES_ACTOR_TOKEN \
    WEAVE_NEXTCLOUD_FILES_WEBDAV_ROOT_PATH \
    WEAVE_CALDAV_BASE_URL \
    WEAVE_CALDAV_CALENDAR_PATH_TEMPLATE \
    WEAVE_CALDAV_AUTH_MODE \
    WEAVE_CALDAV_BACKEND_USERNAME \
    WEAVE_CALDAV_BACKEND_TOKEN \
    WEAVE_CALDAV_REQUEST_TIMEOUT_SECONDS \
    WEAVE_CALDAV_EXTERNAL_DISCOVERY_URL \
    WEAVE_CALDAV_EXTERNAL_CREDENTIAL_MODE \
    WEAVE_CALDAV_EXTERNAL_PROFILE_PASSWORD_MODE \
    WEAVE_CALDAV_EXTERNAL_PRIVATE_USER_CALENDARS; do
    assert_backend_env_present "${name}"
  done

  actor_model="$(container_env_value weave-backend WEAVE_NEXTCLOUD_FILES_ACTOR_MODEL)"
  [[ "${actor_model}" == "backend-service-account" ]] || fail "Operator check failed: unsupported files actor model ${actor_model}"

  actor_username="$(container_env_value weave-backend WEAVE_NEXTCLOUD_FILES_ACTOR_USERNAME)"
  actor_token="$(container_env_value weave-backend WEAVE_CALDAV_BACKEND_TOKEN)"
  caldav_username="$(container_env_value weave-backend WEAVE_CALDAV_BACKEND_USERNAME)"
  [[ "${actor_username}" == "${caldav_username}" ]] || fail "Operator check failed: files and calendar adapters should use the same backend-owned Nextcloud actor username"

  webdav_root="$(container_env_value weave-backend WEAVE_NEXTCLOUD_FILES_WEBDAV_ROOT_PATH)"
  [[ "${webdav_root}" == "/remote.php/dav/files" ]] || fail "Operator check failed: unexpected files WebDAV root path ${webdav_root}"

  backend_nextcloud_base_url="$(container_env_value weave-backend WEAVE_NEXTCLOUD_BASE_URL)"
  caldav_base_url="$(container_env_value weave-backend WEAVE_CALDAV_BASE_URL)"
  [[ "${caldav_base_url}" == "${backend_nextcloud_base_url}" ]] || fail "Operator check failed: CalDAV base URL should match the backend Nextcloud adapter base URL"

  caldav_template="$(container_env_value weave-backend WEAVE_CALDAV_CALENDAR_PATH_TEMPLATE)"
  [[ "${caldav_template}" != *"{user}"* ]] || fail "Operator check failed: CalDAV calendar path template must target the backend actor workspace calendar while team/channel scopes are implemented, not unresolved private personal calendars"
  [[ "${caldav_template}" == "/remote.php/dav/calendars/${actor_username}/personal/" ]] || fail "Operator check failed: CalDAV calendar path template must target the backend actor workspace calendar while team/channel scopes are implemented"

  caldav_auth_mode="$(container_env_value weave-backend WEAVE_CALDAV_AUTH_MODE)"
  [[ "${caldav_auth_mode}" == "BASIC" || "${caldav_auth_mode}" == "BEARER" ]] || fail "Operator check failed: unsupported CalDAV auth mode ${caldav_auth_mode}"

  local external_discovery_url
  local external_credential_mode
  local external_profile_password_mode
  local external_private_user_calendars

  external_discovery_url="$(container_env_value weave-backend WEAVE_CALDAV_EXTERNAL_DISCOVERY_URL)"
  [[ "${external_discovery_url}" == */remote.php/dav ]] || fail "Operator check failed: external CalDAV discovery URL must point at /remote.php/dav"
  [[ "${external_discovery_url}" != *"${caldav_username}"* ]] || fail "Operator check failed: external CalDAV discovery URL must not include backend actor identity"

  external_credential_mode="$(container_env_value weave-backend WEAVE_CALDAV_EXTERNAL_CREDENTIAL_MODE)"
  [[ "${external_credential_mode}" == "nextcloud-login-flow-app-password" ]] || fail "Operator check failed: external CalDAV credential mode must require revocable user-owned app passwords"

  external_profile_password_mode="$(container_env_value weave-backend WEAVE_CALDAV_EXTERNAL_PROFILE_PASSWORD_MODE)"
  [[ "${external_profile_password_mode}" == "omit" ]] || fail "Operator check failed: external CalDAV profiles must omit passwords"

  external_private_user_calendars="$(container_env_value weave-backend WEAVE_CALDAV_EXTERNAL_PRIVATE_USER_CALENDARS)"
  [[ "${external_private_user_calendars}" == "disabled" ]] || fail "Operator check failed: private personal CalDAV calendars must stay disabled until provisioning/sharing is tested"

  docker exec --user www-data weave-nextcloud php occ user:info "${actor_username}" >/dev/null 2>&1 || \
    fail "Operator check failed: Nextcloud backend actor user is not provisioned"

  for calendar_id in personal weave-team-engineering weave-channel-engineering-general; do
    assert_nextcloud_backend_actor_calendar "${actor_username}" "${actor_token}" "${calendar_id}"
  done

  if [[ -f "${APP_CONFIG_ENV_FILE}" ]]; then
    ! grep -Eq 'WEAVE_NEXTCLOUD_FILES_ACTOR_TOKEN|WEAVE_CALDAV_BACKEND_TOKEN|TF_VAR_nextcloud_backend_actor_token' "${APP_CONFIG_ENV_FILE}" || \
      fail "Operator check failed: no-secret app config exposes backend Nextcloud actor secrets"
  fi
}

assert_authenticated_backend_facades_accept_test_user() {
  local token_endpoint
  local token_response
  local access_token
  local profile_response
  local profile_readiness
  local provider_status
  local files_status

  if [[ "${TF_VAR_create_test_user:-false}" != "true" ]]; then
    return
  fi

  : "${WEAVE_OIDC_CLIENT_ID:=weave-app}"
  : "${WEAVE_TEST_USERNAME:=test}"
  : "${WEAVE_TEST_PASSWORD:=${TF_VAR_test_user_password:-}}"
  [[ -n "${WEAVE_TEST_PASSWORD}" ]] || fail "Operator check failed: TF_VAR_create_test_user=true but no WEAVE_TEST_PASSWORD/TF_VAR_test_user_password is available"

  log "Checking authenticated backend facade token contract..."
  token_endpoint="${WEAVE_OIDC_ISSUER_URL}/protocol/openid-connect/token"
  token_response="$(curl_form "${token_endpoint}" \
    --data-urlencode grant_type=password \
    --data-urlencode client_id="${WEAVE_OIDC_CLIENT_ID}" \
    --data-urlencode username="${WEAVE_TEST_USERNAME}" \
    --data-urlencode password="${WEAVE_TEST_PASSWORD}" \
    --data-urlencode scope='openid profile email weave:workspace')"
  access_token="$(jq -r '.access_token // empty' <<<"${token_response}")"
  [[ -n "${access_token}" ]] || fail "Operator check failed: Keycloak did not mint a test-user app access token"

  profile_response="$(curl_auth_json "${access_token}" "${WEAVE_BASE_URL}/me")"
  assert_json "${profile_response}" ".email == \"${WEAVE_TEST_USERNAME}\"" "backend should accept the test-user app token"

  profile_readiness="$(curl_auth_json "${access_token}" "${WEAVE_BASE_URL}/profile/readiness")"
  assert_json "${profile_readiness}" '.contractId == "CEFACADE" and .endpoint == "/profile/readiness"' "profile readiness should expose the CEFACADE /profile/readiness contract"
  assert_json "${profile_readiness}" '.backendOwnedFacade == true and .directProviderCallsAllowed == false and .supportSafe == true' "profile readiness should remain backend-owned and support-safe"

  provider_status="$(curl_auth_json "${access_token}" "${WEAVE_BASE_URL}/providers/status")"
  assert_json "${provider_status}" '.backendOwnedFacades == true and .flutterDirectProviderCallsAllowed == false and .supportSafe == true' "provider registry should be visible through the backend facade and disallow direct Flutter provider calls"
  assert_json "${provider_status}" '[.providers[] | select(.module == "files" and .providerKey == "nextcloud-files") | select(.failClosed == true and .supportSafe == true)] | length == 1' "Nextcloud WebDAV/files provider seam should be visible and support-safe"
  assert_json "${provider_status}" '[.providers[] | select(.module == "calendar" and .providerKey == "nextcloud-caldav") | select(.failClosed == true and .supportSafe == true)] | length == 1' "Nextcloud CalDAV provider seam should be visible and support-safe"
  assert_json "${provider_status}" '[.providers[] | select(.module == "contacts" and .providerKey == "nextcloud-carddav") | select(.failClosed == true and .supportSafe == true)] | length == 1' "Nextcloud CardDAV provider seam should be visible and support-safe"
  assert_json "${provider_status}" '[.providers[] | select(.module == "forms" and .providerKey == "nextcloud-forms") | select(.failClosed == true and .supportSafe == true)] | length == 1' "Nextcloud Forms provider seam should be visible and support-safe"
  assert_json "${provider_status}" '[.providers[] | select(.module == "matrix" and .providerKey == "synapse-homeserver") | select(.failClosed == true and .supportSafe == true)] | length == 1' "Synapse/Matrix provider seam should be visible and support-safe"
  assert_json "${provider_status}" '[.providers[] | select(.module == "matrix-auth" and .providerKey == "matrix-authentication-service") | select(.failClosed == true and .supportSafe == true)] | length == 1' "MAS provider seam should be visible and support-safe"
  assert_json "${provider_status}" '[.providers[] | select(.module == "meetings" and .providerKey == "livekit") | select(.enabled == false and .configured == false and .failClosed == true and .supportSafe == true)] | length == 1' "LiveKit meetings provider seam should default fail-closed and support-safe"
  assert_json "${provider_status}" '[.providers[] | select(.module == "office" or .module == "contacts" or .module == "forms" or .module == "meetings" or .module == "source-control" or .module == "issue-tracker" or .module == "ci" or .module == "release") | select(.enabled == false and .configured == false and .failClosed == true and .supportSafe == true)] | length >= 8' "optional providers should default fail-closed and support-safe"
  ! grep -Eiq 'Authorization|api[_-]?token|/api/v3/|/work_packages/|/projects/' <<<"${provider_status}" || \
    fail "Operator check failed: provider registry leaked provider credentials or raw upstream paths"

  files_status="$(curl_auth_status "${access_token}" "${WEAVE_BASE_URL}/files" || true)"
  [[ "${files_status}" == 2* ]] || fail "Operator check failed: authenticated files facade rejected the test-user app token with HTTP ${files_status}"
}

assert_backend_product_gate_config() {
  local boards_runtime_count
  local boards_runtime_enabled
  local name

  log "Checking Calendar/Boards product gates..."
  for name in \
    WEAVE_WORKSPACE_CALENDAR_ENABLED \
    WEAVE_WORKSPACE_CALENDAR_READINESS \
    WEAVE_WORKSPACE_BOARDS_ENABLED \
    WEAVE_WORKSPACE_BOARDS_READINESS \
    WEAVE_BOARDS_PREVIEW_RUNTIME_ENABLED; do
    assert_backend_env_present "${name}"
  done

  [[ "$(container_env_value weave-backend WEAVE_WORKSPACE_CALENDAR_ENABLED)" == "true" ]] || \
    fail "Operator check failed: Calendar capability must be enabled for the active workspace/team/channel facade path"
  [[ "$(container_env_value weave-backend WEAVE_WORKSPACE_CALENDAR_READINESS)" == "ready" ]] || \
    fail "Operator check failed: Calendar capability readiness must be ready when infra wires the active facade"
  [[ "$(container_env_value weave-backend WEAVE_WORKSPACE_BOARDS_ENABLED)" == "true" ]] || \
    fail "Operator check failed: Boards capability must be enabled for guarded active preview validation"
  [[ "$(container_env_value weave-backend WEAVE_WORKSPACE_BOARDS_READINESS)" == "ready" ]] || \
    fail "Operator check failed: Boards capability readiness must be ready when infra wires the guarded facade"

  boards_runtime_count="$(container_env_count weave-backend WEAVE_BOARDS_PREVIEW_RUNTIME_ENABLED)"
  [[ "${boards_runtime_count}" == "1" ]] || \
    fail "Operator check failed: WEAVE_BOARDS_PREVIEW_RUNTIME_ENABLED must be defined exactly once so the runtime gate is unambiguous"

  boards_runtime_enabled="$(container_env_value weave-backend WEAVE_BOARDS_PREVIEW_RUNTIME_ENABLED)"
  [[ "${boards_runtime_enabled}" == "true" || "${boards_runtime_enabled}" == "false" ]] || \
    fail "Operator check failed: WEAVE_BOARDS_PREVIEW_RUNTIME_ENABLED must be true or false"
}

require_command curl
require_command docker
require_command jq
load_bootstrap_env
# shellcheck disable=SC1090
source "${SYNAPSE_VOLUME_HELPER}"

: "${WEAVE_BASE_URL:=$(api_public_url)/api}"
: "${WEAVE_PUBLIC_BASE_URL:=$(product_public_url)}"
: "${WEAVE_OIDC_ISSUER_URL:=$(public_url "${TF_VAR_auth_subdomain:-auth}")/realms/${TF_VAR_tenant_slug:-weave}}"
: "${WEAVE_NEXTCLOUD_BASE_URL:=$(public_url "${TF_VAR_nextcloud_subdomain:-files}")}"
: "${WEAVE_MATRIX_HOMESERVER_URL:=$(public_url "${TF_VAR_matrix_subdomain:-matrix}")}"

synapse_operator_diagnose_volume

log "Checking core containers..."
for container in weave-proxy weave-keycloak weave-backend weave-mas weave-synapse weave-nextcloud weave-db; do
  assert_container_running "${container}"
done

log "Checking loopback health endpoints..."
assert_http_200 "Keycloak management" "http://127.0.0.1:${TF_VAR_keycloak_management_host_port:-49000}/health/ready"
assert_http_200 "Weave backend" "http://127.0.0.1:${TF_VAR_backend_host_port:-48084}/api/health/ready"
assert_http_200 "MAS" "http://127.0.0.1:${TF_VAR_mas_host_port:-48082}/health"
assert_http_200 "Synapse" "http://127.0.0.1:${TF_VAR_synapse_host_port:-48008}/_matrix/client/versions"

log "Checking public product, issuer, API, files, and matrix routes..."
product_status="$(curl_status "${WEAVE_PUBLIC_BASE_URL}/")"
[[ "${product_status}" == "200" ]] || fail "Operator check failed: Weave product gateway returned HTTP ${product_status} at ${WEAVE_PUBLIC_BASE_URL}/"

files_product_status="$(curl_status "${WEAVE_PUBLIC_BASE_URL}/files")"
[[ "${files_product_status}" == "200" ]] || fail "Operator check failed: Weave product files route returned HTTP ${files_product_status} at ${WEAVE_PUBLIC_BASE_URL}/files"

calendar_product_status="$(curl_status "${WEAVE_PUBLIC_BASE_URL}/calendar")"
[[ "${calendar_product_status}" == "200" ]] || fail "Operator check failed: Weave product calendar route returned HTTP ${calendar_product_status} at ${WEAVE_PUBLIC_BASE_URL}/calendar"

issuer_config="$(curl_json "${WEAVE_OIDC_ISSUER_URL}/.well-known/openid-configuration")"
assert_json "${issuer_config}" ".issuer == \"${WEAVE_OIDC_ISSUER_URL}\"" "public Keycloak issuer should match the configured release URL"

backend_health="$(curl_json "${WEAVE_BASE_URL}/health/ready")"
assert_json "${backend_health}" '.status == "up"' "public backend readiness should report up"

platform_config="$(curl_json "${WEAVE_BASE_URL}/platform/config")"
assert_json "${platform_config}" '.features.chatE2ee == false and .features.matrixFederation == false' "platform config should not claim Matrix E2EE or federation readiness"

nextcloud_status="$(curl_json "${WEAVE_NEXTCLOUD_BASE_URL}/status.php")"
assert_json "${nextcloud_status}" '.installed == true' "Nextcloud should be installed"

assert_backend_nextcloud_actor_config
assert_backend_product_gate_config
assert_backend_provider_stack_config
assert_backend_boards_openproject_config
assert_authenticated_backend_facades_accept_test_user

if [[ "${WEAVE_PROVIDER_STACK_ENDPOINT_CHECKS:-false}" == "true" ]]; then
  bash "${ROOT_DIR}/provider-stack-fail-closed-check.sh" --endpoints
else
  log "Provider-stack endpoint fail-closed checks skipped; set WEAVE_PROVIDER_STACK_ENDPOINT_CHECKS=true when running a backend image with provider endpoints."
fi

nextcloud_bearer_validation="$(docker exec --user www-data weave-nextcloud php occ config:system:get user_oidc oidc_provider_bearer_validation 2>/dev/null || true)"
[[ "${nextcloud_bearer_validation}" == "true" ]] || fail "Operator check failed: Nextcloud user_oidc bearer validation is not enabled"

nextcloud_oidc_provider="$(docker exec --user www-data weave-nextcloud php occ user_oidc:provider --output=json keycloak)"
assert_json "${nextcloud_oidc_provider}" '.settings.checkBearer == true or .settings.checkBearer == "1" or .settings.checkBearer == 1' "Nextcloud OIDC provider should validate Bearer tokens"
assert_json "${nextcloud_oidc_provider}" '.settings.bearerProvisioning == true or .settings.bearerProvisioning == "1" or .settings.bearerProvisioning == 1' "Nextcloud OIDC provider should provision Bearer-token users"

mas_discovery="$(curl_json "${WEAVE_MATRIX_HOMESERVER_URL}/.well-known/openid-configuration")"
assert_json "${mas_discovery}" ".issuer == \"${WEAVE_MATRIX_HOMESERVER_URL}/\"" "MAS issuer should match the public matrix URL"

matrix_versions="$(curl_json "${WEAVE_MATRIX_HOMESERVER_URL}/_matrix/client/versions")"
assert_json "${matrix_versions}" '.versions | type == "array"' "public Matrix client versions route should be served by Synapse"

matrix_auth_metadata="$(curl_json "${WEAVE_MATRIX_HOMESERVER_URL}/_matrix/client/v1/auth_metadata")"
assert_json "${matrix_auth_metadata}" ".issuer == \"${WEAVE_MATRIX_HOMESERVER_URL}/\"" "Matrix OAuth metadata should be served by MAS"
assert_json "${matrix_auth_metadata}" '.authorization_endpoint | contains("/authorize")' "Matrix OAuth metadata should expose the MAS authorization endpoint"

log "Checking default Matrix room aliases..."
matrix_homeserver="${WEAVE_MATRIX_HOMESERVER_URL#*://}"
matrix_homeserver="${matrix_homeserver%%/*}"
matrix_homeserver="${matrix_homeserver%%:*}"
matrix_space_alias="#${WEAVE_MATRIX_WORKSPACE_ALIAS_LOCALPART:-weave-workspace}:${matrix_homeserver}"
matrix_announcements_alias="#${WEAVE_MATRIX_ANNOUNCEMENTS_ALIAS_LOCALPART:-announcements}:${matrix_homeserver}"
matrix_general_alias="#${WEAVE_MATRIX_GENERAL_ALIAS_LOCALPART:-general}:${matrix_homeserver}"
matrix_help_alias="#${WEAVE_MATRIX_HELP_ALIAS_LOCALPART:-help}:${matrix_homeserver}"

matrix_space_id="$(matrix_room_id_by_alias "${WEAVE_MATRIX_HOMESERVER_URL}" "${matrix_space_alias}")"
matrix_announcements_id="$(matrix_room_id_by_alias "${WEAVE_MATRIX_HOMESERVER_URL}" "${matrix_announcements_alias}")"
matrix_general_id="$(matrix_room_id_by_alias "${WEAVE_MATRIX_HOMESERVER_URL}" "${matrix_general_alias}")"
matrix_help_id="$(matrix_room_id_by_alias "${WEAVE_MATRIX_HOMESERVER_URL}" "${matrix_help_alias}")"
[[ "${matrix_space_id}" == \!* ]] || fail "Operator check failed: default Matrix space alias did not resolve"
[[ "${matrix_announcements_id}" == \!* ]] || fail "Operator check failed: announcements room alias did not resolve"
[[ "${matrix_general_id}" == \!* ]] || fail "Operator check failed: general room alias did not resolve"
[[ "${matrix_help_id}" == \!* ]] || fail "Operator check failed: help room alias did not resolve"

log "Checking Matrix E2EE posture gates..."
assert_matrix_room_unencrypted_until_e2ee_promoted "workspace space" "${matrix_space_id}"
assert_matrix_room_unencrypted_until_e2ee_promoted "announcements" "${matrix_announcements_id}"
assert_matrix_room_unencrypted_until_e2ee_promoted "general" "${matrix_general_id}"
assert_matrix_room_unencrypted_until_e2ee_promoted "help" "${matrix_help_id}"
check_matrix_provisioner_key_backup_diagnostic

log "Operator checks passed."
