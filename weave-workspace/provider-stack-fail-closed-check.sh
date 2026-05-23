#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
BOOTSTRAP_ENV_FILE="${WEAVE_BOOTSTRAP_ENV:-${ROOT_DIR}/.generated/bootstrap.env}"
APP_CONFIG_ENV_FILE="${ROOT_DIR}/.generated/app-config.env"
MODE="${1:---static}"

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

assert_file_contains() {
  local file="$1"
  local needle="$2"

  grep -Fq -- "${needle}" "${file}" || fail "Provider fail-closed check failed: ${file} does not contain ${needle}"
}

assert_file_not_contains() {
  local file="$1"
  local needle="$2"

  if [[ -f "${file}" ]] && grep -Fq -- "${needle}" "${file}"; then
    fail "Provider fail-closed check failed: ${file} must not contain ${needle}"
  fi
}

assert_json() {
  local json="$1"
  local jq_filter="$2"
  local description="$3"

  jq -e "${jq_filter}" >/dev/null <<<"${json}" || fail "Provider fail-closed check failed: ${description}"
}

assert_no_secret_markers() {
  local label="$1"
  local body="$2"
  local marker
  local markers=(
    'access_token'
    'Authorization: Bearer'
    'CI_JOB_TOKEN'
    'webhook_secret'
    'WEAVE_DEVOPS_GITLAB_API_TOKEN'
    'WEAVE_DEVOPS_FORGEJO_API_TOKEN'
    'WEAVE_OFFICE_ONLYOFFICE_JWT_SECRET'
    'TF_VAR_devops_gitlab_api_token'
    'TF_VAR_devops_forgejo_api_token'
    'TF_VAR_office_onlyoffice_jwt_secret'
  )

  for marker in "${markers[@]}"; do
    [[ "${body}" != *"${marker}"* ]] || fail "Provider fail-closed check failed: ${label} leaked marker ${marker}"
  done
}

run_static_checks() {
  log "Checking static provider-stack fail-closed wiring..."

  assert_file_contains "${ROOT_DIR}/01-infrastructure/modules/backend/main.tf" 'WEAVE_PROVIDER_STACK_PROFILE'
  assert_file_contains "${ROOT_DIR}/01-infrastructure/modules/backend/main.tf" 'WEAVE_MATRIX_HOMESERVER_URL'
  assert_file_contains "${ROOT_DIR}/01-infrastructure/modules/backend/main.tf" 'WEAVE_NEXTCLOUD_BASE_URL'
  assert_file_contains "${ROOT_DIR}/01-infrastructure/modules/backend/main.tf" 'WEAVE_NEXTCLOUD_FILES_WEBDAV_ROOT_PATH'
  assert_file_contains "${ROOT_DIR}/01-infrastructure/modules/backend/main.tf" 'WEAVE_CALDAV_BASE_URL'
  assert_file_contains "${ROOT_DIR}/01-infrastructure/modules/backend/main.tf" 'WEAVE_DEVOPS_GITLAB_RUNTIME_ENABLED'
  assert_file_contains "${ROOT_DIR}/01-infrastructure/modules/backend/main.tf" 'WEAVE_DEVOPS_FORGEJO_RUNTIME_ENABLED'
  assert_file_contains "${ROOT_DIR}/01-infrastructure/modules/backend/main.tf" 'WEAVE_OFFICE_ONLYOFFICE_RUNTIME_ENABLED'
  assert_file_contains "${ROOT_DIR}/01-infrastructure/modules/backend/main.tf" 'WEAVE_OFFICE_NEXTCLOUD_INTEGRATION_MODE'
  assert_file_contains "${ROOT_DIR}/01-infrastructure/modules/backend/main.tf" 'WEAVE_GROUPWARE_CONTACTS_RUNTIME_ENABLED'
  assert_file_contains "${ROOT_DIR}/01-infrastructure/modules/backend/main.tf" 'WEAVE_GROUPWARE_FORMS_RUNTIME_ENABLED'
  assert_file_contains "${ROOT_DIR}/01-infrastructure/modules/backend/main.tf" 'WEAVE_BOARDS_NEXTCLOUD_DECK_RUNTIME_ENABLED'

  assert_file_contains "${ROOT_DIR}/.env.example" 'TF_VAR_provider_stack_profile=fail-closed'
  assert_file_contains "${ROOT_DIR}/.env.example" 'TF_VAR_matrix_subdomain=matrix'
  assert_file_contains "${ROOT_DIR}/.env.example" 'TF_VAR_nextcloud_subdomain=files'
  assert_file_contains "${ROOT_DIR}/.env.example" 'TF_VAR_devops_primary_provider=gitlab-ce-foss'
  assert_file_contains "${ROOT_DIR}/.env.example" 'TF_VAR_devops_gitlab_runtime_enabled=false'
  assert_file_contains "${ROOT_DIR}/.env.example" 'TF_VAR_devops_forgejo_runtime_enabled=false'
  assert_file_contains "${ROOT_DIR}/.env.example" 'TF_VAR_office_primary_provider=onlyoffice-community'
  assert_file_contains "${ROOT_DIR}/.env.example" 'TF_VAR_office_onlyoffice_runtime_enabled=false'
  assert_file_contains "${ROOT_DIR}/.env.example" 'TF_VAR_office_nextcloud_integration_mode=nextcloud-onlyoffice-app-behind-backend-facade'
  assert_file_contains "${ROOT_DIR}/.env.example" 'TF_VAR_groupware_contacts_runtime_enabled=false'
  assert_file_contains "${ROOT_DIR}/.env.example" 'TF_VAR_groupware_forms_runtime_enabled=false'
  assert_file_contains "${ROOT_DIR}/release.env.example" 'TF_VAR_groupware_contacts_runtime_enabled=false'
  assert_file_contains "${ROOT_DIR}/release.env.example" 'TF_VAR_groupware_forms_runtime_enabled=false'
  assert_file_contains "${ROOT_DIR}/.env.example" 'TF_VAR_boards_nextcloud_deck_runtime_enabled=false'

  assert_file_contains "${ROOT_DIR}/docker-compose.provider-stack.yml" 'profiles:'
  assert_file_contains "${ROOT_DIR}/docker-compose.provider-stack.yml" 'gitlab-ce'
  assert_file_contains "${ROOT_DIR}/docker-compose.provider-stack.yml" 'forgejo'
  assert_file_contains "${ROOT_DIR}/docker-compose.provider-stack.yml" 'onlyoffice'

  assert_file_not_contains "${APP_CONFIG_ENV_FILE}" 'TF_VAR_devops_gitlab_api_token'
  assert_file_not_contains "${APP_CONFIG_ENV_FILE}" 'TF_VAR_devops_forgejo_api_token'
  assert_file_not_contains "${APP_CONFIG_ENV_FILE}" 'TF_VAR_office_onlyoffice_jwt_secret'
  assert_file_not_contains "${APP_CONFIG_ENV_FILE}" 'WEAVE_DEVOPS_GITLAB_API_TOKEN'
  assert_file_not_contains "${APP_CONFIG_ENV_FILE}" 'WEAVE_DEVOPS_FORGEJO_API_TOKEN'
  assert_file_not_contains "${APP_CONFIG_ENV_FILE}" 'WEAVE_OFFICE_ONLYOFFICE_JWT_SECRET'

  log "Static provider-stack fail-closed wiring passed."
}

host_port_from_url() {
  local url="$1"
  local scheme="${url%%://*}"
  local host_port

  host_port="${url#*://}"
  host_port="${host_port%%/*}"
  if [[ "${host_port}" != *:* ]]; then
    case "${scheme}" in
      https) host_port="${host_port}:443" ;;
      http) host_port="${host_port}:80" ;;
    esac
  fi

  printf '%s\n' "${host_port}"
}

curl_args_for_url() {
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

curl_auth_json() {
  local token="$1"
  local url="$2"
  local -a args=()

  while IFS= read -r -d '' arg; do
    args+=("${arg}")
  done < <(curl_args_for_url "${url}")

  curl "${args[@]}" --header "Authorization: Bearer ${token}" "${url}"
}

fetch_workspace_token() {
  local token_endpoint="${WEAVE_OIDC_ISSUER_URL}/protocol/openid-connect/token"
  local username="${WEAVE_TEST_USERNAME:-test@weave.local}"
  local password="${WEAVE_TEST_PASSWORD:-${TF_VAR_test_user_password:-}}"
  local client_id="${WEAVE_OIDC_CLIENT_ID:-weave-app}"
  local -a args=()
  local response

  [[ -n "${password}" ]] || fail "Provider endpoint check requires WEAVE_TEST_PASSWORD or TF_VAR_test_user_password"

  while IFS= read -r -d '' arg; do
    args+=("${arg}")
  done < <(curl_args_for_url "${token_endpoint}")

  response="$(curl "${args[@]}" \
    --header 'content-type: application/x-www-form-urlencoded' \
    --data-urlencode grant_type=password \
    --data-urlencode "client_id=${client_id}" \
    --data-urlencode "username=${username}" \
    --data-urlencode "password=${password}" \
    --data-urlencode 'scope=openid profile email weave:workspace' \
    "${token_endpoint}")"

  jq -r '.access_token // empty' <<<"${response}"
}

run_endpoint_checks() {
  require_command curl
  require_command jq

  [[ -f "${BOOTSTRAP_ENV_FILE}" ]] || fail "Provider endpoint check requires bootstrap env at ${BOOTSTRAP_ENV_FILE}"
  # shellcheck disable=SC1090
  source "${BOOTSTRAP_ENV_FILE}"

  : "${WEAVE_BASE_URL:?WEAVE_BASE_URL missing from bootstrap env}"
  : "${WEAVE_OIDC_ISSUER_URL:?WEAVE_OIDC_ISSUER_URL missing from bootstrap env}"

  local token
  local providers_json
  local office_json
  local devops_json

  log "Checking live provider endpoints are fail-closed and secret-free..."
  token="$(fetch_workspace_token)"
  [[ -n "${token}" ]] || fail "Provider endpoint check could not obtain a workspace-scoped test token"

  providers_json="$(curl_auth_json "${token}" "${WEAVE_BASE_URL}/providers/status")"
  assert_json "${providers_json}" '.backendOwnedFacades == true and .flutterDirectProviderCallsAllowed == false and .supportSafe == true' 'provider registry must be backend-owned/support-safe'
  assert_json "${providers_json}" '[.providers[] | select(.module == "files" and .providerKey == "nextcloud-files" and .failClosed == true)] | length == 1' 'Nextcloud WebDAV/files provider seam must be present and fail-closed when unconfigured'
  assert_json "${providers_json}" '[.providers[] | select(.module == "calendar" and .providerKey == "nextcloud-caldav" and .failClosed == true)] | length == 1' 'Nextcloud CalDAV calendar provider seam must be present and fail-closed when unconfigured'
  assert_json "${providers_json}" '[.providers[] | select(.module == "contacts" and .providerKey == "nextcloud-carddav" and .failClosed == true)] | length == 1' 'Nextcloud CardDAV contacts provider seam must be present and fail-closed'
  assert_json "${providers_json}" '[.providers[] | select(.module == "forms" and .providerKey == "nextcloud-forms" and .failClosed == true)] | length == 1' 'Nextcloud Forms provider seam must be present and fail-closed'
  assert_json "${providers_json}" '[.providers[] | select(.module == "matrix" and .providerKey == "synapse-homeserver" and .failClosed == true and .supportSafe == true)] | length == 1' 'Synapse/Matrix provider seam must be present and support-safe'
  assert_json "${providers_json}" '[.providers[] | select(.module == "matrix-auth" and .providerKey == "matrix-authentication-service" and .failClosed == true and .supportSafe == true)] | length == 1' 'MAS provider seam must be present and support-safe'
  assert_json "${providers_json}" '[.providers[] | select(.module == "meetings" and .providerKey == "matrix-meetings" and .enabled == false and .configured == false and .failClosed == true)] | length == 1' 'Meeting/video-call provider seam must be present and fail-closed until promoted'
  assert_json "${providers_json}" '[.providers[] | select(.module == "boards" and .providerKey == "openproject-primary" and .failClosed == true)] | length == 1' 'OpenProject Boards provider seam must be present and fail-closed when unconfigured'
  assert_json "${providers_json}" '[.providers[] | select(.module == "source-control" and .providerKey == "gitlab-ce-foss" and .enabled == false and .configured == false and .failClosed == true)] | length == 1' 'GitLab CE/FOSS source-control provider must be disabled/fail-closed'
  assert_json "${providers_json}" '[.providers[] | select(.module == "source-control" and .providerKey == "forgejo" and .enabled == false and .configured == false and .failClosed == true)] | length == 1' 'Forgejo source-control provider must be disabled/fail-closed'
  assert_json "${providers_json}" '[.providers[] | select(.module == "office" and .providerKey == "onlyoffice-community" and .enabled == false and .configured == false and .failClosed == true)] | length == 1' 'ONLYOFFICE provider must be disabled/fail-closed'
  assert_no_secret_markers 'providers/status' "${providers_json}"

  office_json="$(curl_auth_json "${token}" "${WEAVE_BASE_URL}/office/capabilities")"
  assert_json "${office_json}" '.enabled == false and .configured == false and .launchMode == "unavailable" and .defaultProvider == "onlyoffice-community"' 'Office capabilities must not promise runtime availability'
  assert_json "${office_json}" '.capabilities.view == false and .capabilities.edit == false and .capabilities.comment == false and .capabilities.review == false and .capabilities.formFill == false' 'Office capability flags must remain false while unavailable'
  assert_no_secret_markers 'office/capabilities' "${office_json}"

  devops_json="$(curl_auth_json "${token}" "${WEAVE_BASE_URL}/workspaces/workspace-default/channels/channel-general/devops/summary")"
  assert_json "${devops_json}" '.readOnly == true and .paidFeaturesRequired == false and .supportSafe == true' 'DevOps summary must be read-only/support-safe and free of paid-feature dependencies'
  assert_json "${devops_json}" '(.linkedProjects | length) == 0 and (.repositories | length) == 0 and (.openIssues | length) == 0 and (.mergeRequests | length) == 0 and (.pipelines | length) == 0 and (.releases | length) == 0' 'DevOps summary must be empty while providers are not configured'
  assert_json "${devops_json}" '[.providerReadiness[] | select(.providerKey == "gitlab-ce-foss" and .enabled == false and .configured == false and .readiness == "not_configured")] | length >= 1' 'GitLab CE/FOSS DevOps readiness must be not_configured'
  assert_json "${devops_json}" '[.providerReadiness[] | select(.providerKey == "forgejo" and .enabled == false and .configured == false and .readiness == "not_configured")] | length >= 1' 'Forgejo DevOps readiness must be not_configured'
  assert_no_secret_markers 'devops/summary' "${devops_json}"

  log "Live provider endpoint fail-closed checks passed."
}

case "${MODE}" in
  --static)
    run_static_checks
    ;;
  --endpoints)
    run_endpoint_checks
    ;;
  --all)
    run_static_checks
    run_endpoint_checks
    ;;
  *)
    fail "Usage: $0 [--static|--endpoints|--all]"
    ;;
esac
