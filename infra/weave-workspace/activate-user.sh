#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
BOOTSTRAP_ENV_FILE="${ROOT_DIR}/.generated/bootstrap.env"

TENANT_REALM="${TF_VAR_tenant_slug:-weave}"
USERNAME=""
EMAIL=""
DISPLAY_NAME=""
ROLE="member"
WORKSPACE_GROUP=""
INVITE_REF=""
ACTIVATION_LIFESPAN_SECONDS="900"
ACTIVATION_CLIENT_ID="weave-app"
ACTIVATION_REDIRECT_URI="com.massimotter.weave:/oauthredirect"
REQUIRED_ACTIONS="VERIFY_EMAIL,UPDATE_PASSWORD"
EVIDENCE_FILE=""
DRY_RUN="false"

log() { printf '%s\n' "$*"; }
fail() { printf '%s\n' "$*" >&2; exit 1; }

usage() {
  cat <<'EOF'
Usage: ./activate-user.sh --username USERNAME --email EMAIL --display-name NAME --role owner|admin|member|guest [options]

Local/dev owner/admin helper for creating a one-time Weave activation invite in Keycloak.

Required:
  --username VALUE        Keycloak username / preferred username.
  --email VALUE           User email address.
  --display-name VALUE    Display name shown in Weave profile/onboarding.
  --role VALUE            MVP product role: owner, admin, member, or guest.

Options:
  --workspace-group VALUE Group claim to assign; default: role-mapped workspace-owners/admins/members/guests.
  --invite-ref VALUE      Non-secret invite/handoff reference. Default: activation-USERNAME.
  --activation-lifespan SECONDS
                          Keycloak required-action email lifetime. Default: 900.
  --activation-client-id VALUE
                          OIDC client used after activation. Default: weave-app.
  --activation-redirect-uri URI
                          Redirect after required actions. Default: com.massimotter.weave:/oauthredirect.
  --required-actions CSV  Keycloak required actions. Default: VERIFY_EMAIL,UPDATE_PASSWORD.
  --evidence-file PATH    Write support-safe activation evidence JSON.
  --tenant-realm VALUE    Keycloak realm. Default: TF_VAR_tenant_slug or weave.
  --dry-run               Validate and print the support-safe plan without contacting Keycloak.
  -h, --help              Show this help.

The helper loads weave-workspace/.generated/bootstrap.env when present and uses:
  TF_VAR_keycloak_admin_username
  TF_VAR_keycloak_admin_password
  TF_VAR_public_scheme / TF_VAR_tenant_domain / TF_VAR_auth_subdomain / TF_VAR_proxy_host_port
  TF_VAR_caddy_tls_ca_file or WEAVE_TLS_CA_FILE when local TLS needs a custom CA.
EOF
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

keycloak_public_url() {
  printf '%s://%s.%s%s' \
    "${TF_VAR_public_scheme:-https}" \
    "${TF_VAR_auth_subdomain:-auth}" \
    "${TF_VAR_tenant_domain:-weave.test}" \
    "$(public_port_suffix)"
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --username) USERNAME="${2:-}"; shift 2 ;;
      --email) EMAIL="${2:-}"; shift 2 ;;
      --display-name) DISPLAY_NAME="${2:-}"; shift 2 ;;
      --role) ROLE="${2:-}"; shift 2 ;;
      --workspace-group) WORKSPACE_GROUP="${2:-}"; shift 2 ;;
      --password|--permanent-password)
        fail "$1 is no longer supported. Use the required-action activation email flow; do not create or print initial passwords."
        ;;
      --invite-ref) INVITE_REF="${2:-}"; shift 2 ;;
      --activation-lifespan) ACTIVATION_LIFESPAN_SECONDS="${2:-}"; shift 2 ;;
      --activation-client-id) ACTIVATION_CLIENT_ID="${2:-}"; shift 2 ;;
      --activation-redirect-uri) ACTIVATION_REDIRECT_URI="${2:-}"; shift 2 ;;
      --required-actions) REQUIRED_ACTIONS="${2:-}"; shift 2 ;;
      --evidence-file) EVIDENCE_FILE="${2:-}"; shift 2 ;;
      --tenant-realm) TENANT_REALM="${2:-}"; shift 2 ;;
      --dry-run) DRY_RUN="true"; shift ;;
      -h|--help) usage; exit 0 ;;
      *) fail "Unknown argument: $1" ;;
    esac
  done
}

validate_role() {
  case "${ROLE}" in
    owner|admin|member|guest) ;;
    *) fail "Invalid role '${ROLE}'. Expected one of: owner, admin, member, guest." ;;
  esac
}

default_group_for_role() {
  case "${ROLE}" in
    owner) printf '%s\n' 'workspace-owners' ;;
    admin) printf '%s\n' 'workspace-admins' ;;
    member) printf '%s\n' 'workspace-members' ;;
    guest) printf '%s\n' 'workspace-guests' ;;
    *) fail "Invalid role '${ROLE}'. Expected one of: owner, admin, member, guest." ;;
  esac
}

validate_inputs() {
  [[ -n "${USERNAME}" ]] || fail "--username is required"
  [[ -n "${EMAIL}" ]] || fail "--email is required"
  [[ -n "${DISPLAY_NAME}" ]] || fail "--display-name is required"
  [[ -n "${TENANT_REALM}" ]] || fail "--tenant-realm must not be empty"
  validate_role
  if [[ -z "${INVITE_REF}" ]]; then
    INVITE_REF="activation-${USERNAME}"
  fi
  [[ "${INVITE_REF}" =~ ^[A-Za-z0-9._:-]{6,96}$ ]] || fail "--invite-ref must be a non-secret support-safe reference"
  [[ "${ACTIVATION_LIFESPAN_SECONDS}" =~ ^[0-9]+$ ]] || fail "--activation-lifespan must be seconds"
  (( ACTIVATION_LIFESPAN_SECONDS >= 300 && ACTIVATION_LIFESPAN_SECONDS <= 86400 )) || fail "--activation-lifespan must be between 300 and 86400 seconds"
  [[ -n "${ACTIVATION_CLIENT_ID}" ]] || fail "--activation-client-id must not be empty"
  [[ -n "${ACTIVATION_REDIRECT_URI}" ]] || fail "--activation-redirect-uri must not be empty"
  [[ -n "${REQUIRED_ACTIONS}" ]] || fail "--required-actions must not be empty"
  grep -Eq '(^|,)[[:space:]]*UPDATE_PASSWORD[[:space:]]*(,|$)' <<<"${REQUIRED_ACTIONS}" || fail "--required-actions must include UPDATE_PASSWORD"
  for value in "${INVITE_REF}" "${ACTIVATION_CLIENT_ID}" "${ACTIVATION_REDIRECT_URI}" "${REQUIRED_ACTIONS}"; do
    if grep -Eiq '(password=|token=|access_token|refresh_token|id_token|client_secret|bearer )' <<<"${value}"; then
      fail "activation invite inputs must not contain credential-like material"
    fi
  done
  if [[ -z "${WORKSPACE_GROUP}" ]]; then
    WORKSPACE_GROUP="$(default_group_for_role)"
  fi
  [[ -n "${WORKSPACE_GROUP}" ]] || fail "--workspace-group must not be empty"
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing required command: $1"
}

json_required_actions() {
  jq -cn --arg actions "${REQUIRED_ACTIONS}" \
    '$actions | split(",") | map(gsub("^\\s+|\\s+$"; "")) | map(select(length > 0))'
}

sha256_value() {
  printf '%s' "$1" | shasum -a 256 | awk '{print $1}'
}

redirect_uri_class() {
  case "${ACTIVATION_REDIRECT_URI}" in
    com.massimotter.weave:*) printf 'weave-ios-custom-scheme' ;;
    https:*) printf 'https' ;;
    *) printf 'other' ;;
  esac
}

curl_args() {
  local -a args=(--silent --show-error --fail-with-body)
  if [[ -n "${WEAVE_TLS_CA_FILE:-}" ]]; then
    args+=(--cacert "${WEAVE_TLS_CA_FILE}")
  elif [[ -n "${TF_VAR_caddy_tls_ca_file:-}" && -f "${TF_VAR_caddy_tls_ca_file}" ]]; then
    args+=(--cacert "${TF_VAR_caddy_tls_ca_file}")
  fi
  printf '%s\0' "${args[@]}"
}

curl_keycloak() {
  local method="$1"
  local url="$2"
  local token="${3:-}"
  local body="${4:-}"
  local -a args=()
  while IFS= read -r -d '' arg; do args+=("${arg}"); done < <(curl_args)
  args+=(-X "${method}")
  [[ -n "${token}" ]] && args+=(-H "Authorization: Bearer ${token}")
  if [[ -n "${body}" ]]; then
    args+=(-H 'Content-Type: application/json' --data "${body}")
  fi
  curl "${args[@]}" "${url}"
}

admin_token() {
  local token_url
  token_url="$(keycloak_public_url)/realms/master/protocol/openid-connect/token"
  local -a args=()
  while IFS= read -r -d '' arg; do args+=("${arg}"); done < <(curl_args)
  curl "${args[@]}" -X POST "${token_url}" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode 'client_id=admin-cli' \
    --data-urlencode "username=${TF_VAR_keycloak_admin_username:-admin}" \
    --data-urlencode "password=${TF_VAR_keycloak_admin_password:-}" \
    --data-urlencode 'grant_type=password' |
    jq -r '.access_token // empty'
}

ensure_realm_role() {
  local base_url="$1" token="$2" role_name="$3"
  local encoded_role
  encoded_role="$(jq -nr --arg value "${role_name}" '$value|@uri')"
  if ! curl_keycloak GET "${base_url}/roles/${encoded_role}" "${token}" >/dev/null 2>&1; then
    curl_keycloak POST "${base_url}/roles" "${token}" "$(jq -n --arg name "${role_name}" '{name: $name, description: "Weave MVP product role"}')" >/dev/null
  fi
  curl_keycloak GET "${base_url}/roles/${encoded_role}" "${token}"
}

ensure_group_id() {
  local base_url="$1" token="$2" group_name="$3"
  local encoded_group groups group_id
  encoded_group="$(jq -nr --arg value "${group_name}" '$value|@uri')"
  groups="$(curl_keycloak GET "${base_url}/groups?search=${encoded_group}&exact=true" "${token}")"
  group_id="$(jq -r --arg name "${group_name}" '.[] | select(.name == $name) | .id' <<<"${groups}" | head -n 1)"
  if [[ -z "${group_id}" ]]; then
    curl_keycloak POST "${base_url}/groups" "${token}" "$(jq -n --arg name "${group_name}" '{name: $name}')" >/dev/null
    groups="$(curl_keycloak GET "${base_url}/groups?search=${encoded_group}&exact=true" "${token}")"
    group_id="$(jq -r --arg name "${group_name}" '.[] | select(.name == $name) | .id' <<<"${groups}" | head -n 1)"
  fi
  [[ -n "${group_id}" ]] || fail "Could not create or locate Keycloak group '${group_name}'"
  printf '%s\n' "${group_id}"
}

upsert_user() {
  local base_url="$1" token="$2"
  local encoded_username users user_id payload first_name last_name
  encoded_username="$(jq -nr --arg value "${USERNAME}" '$value|@uri')"
  users="$(curl_keycloak GET "${base_url}/users?username=${encoded_username}&exact=true" "${token}")"
  user_id="$(jq -r --arg username "${USERNAME}" '.[] | select(.username == $username) | .id' <<<"${users}" | head -n 1)"

  first_name="${DISPLAY_NAME%% *}"
  if [[ "${DISPLAY_NAME}" == *' '* ]]; then
    last_name="${DISPLAY_NAME#* }"
  else
    last_name=""
  fi

  payload="$(jq -n \
    --arg username "${USERNAME}" \
    --arg email "${EMAIL}" \
    --arg firstName "${first_name}" \
    --arg lastName "${last_name}" \
    --arg inviteRef "${INVITE_REF}" \
    --argjson requiredActions "$(json_required_actions)" \
    '{
      username: $username,
      email: $email,
      firstName: $firstName,
      lastName: $lastName,
      enabled: true,
      emailVerified: false,
      requiredActions: $requiredActions,
      attributes: {
        weave_invite_ref: [$inviteRef],
        weave_invite_status: ["pending"]
      }
    }')"

  if [[ -z "${user_id}" ]]; then
    curl_keycloak POST "${base_url}/users" "${token}" "${payload}" >/dev/null
    users="$(curl_keycloak GET "${base_url}/users?username=${encoded_username}&exact=true" "${token}")"
    user_id="$(jq -r --arg username "${USERNAME}" '.[] | select(.username == $username) | .id' <<<"${users}" | head -n 1)"
  else
    curl_keycloak PUT "${base_url}/users/${user_id}" "${token}" "${payload}" >/dev/null
  fi

  [[ -n "${user_id}" ]] || fail "Could not create or locate Keycloak user '${USERNAME}'"

  printf '%s\n' "${user_id}"
}

execute_activation_email() {
  local base_url="$1" token="$2" user_id="$3"
  local encoded_redirect encoded_client
  encoded_redirect="$(jq -nr --arg value "${ACTIVATION_REDIRECT_URI}" '$value|@uri')"
  encoded_client="$(jq -nr --arg value "${ACTIVATION_CLIENT_ID}" '$value|@uri')"
  curl_keycloak PUT \
    "${base_url}/users/${user_id}/execute-actions-email?lifespan=${ACTIVATION_LIFESPAN_SECONDS}&client_id=${encoded_client}&redirect_uri=${encoded_redirect}" \
    "${token}" \
    "$(json_required_actions)" >/dev/null
}

write_activation_evidence() {
  local path="$1" mail_sent="$2"
  [[ -n "${path}" ]] || return 0
  mkdir -p "$(dirname -- "${path}")"
  jq -n \
    --arg schemaVersion "weave.dogfood.activation-invite.v1" \
    --arg generatedAt "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" \
    --arg realm "${TENANT_REALM}" \
    --arg usernameSha256 "$(sha256_value "${USERNAME}")" \
    --arg emailSha256 "$(sha256_value "${EMAIL}")" \
    --arg role "${ROLE}" \
    --arg workspaceGroup "${WORKSPACE_GROUP}" \
    --arg inviteRef "${INVITE_REF}" \
    --arg clientId "${ACTIVATION_CLIENT_ID}" \
    --arg redirectUriClass "$(redirect_uri_class)" \
    --argjson activationLifespanSeconds "${ACTIVATION_LIFESPAN_SECONDS}" \
    --argjson requiredActions "$(json_required_actions)" \
    --argjson activationMailSent "${mail_sent}" \
    '{
      schemaVersion: $schemaVersion,
      generatedAt: $generatedAt,
      realm: $realm,
      usernameSha256: $usernameSha256,
      emailSha256: $emailSha256,
      role: $role,
      workspaceGroup: $workspaceGroup,
      inviteRef: $inviteRef,
      activation: {
        mode: "keycloak-required-actions-email",
        requiredActions: $requiredActions,
        lifespanSeconds: $activationLifespanSeconds,
        clientId: $clientId,
        redirectUriClass: $redirectUriClass,
        mailSent: $activationMailSent
      },
      qrOrDeeplinkCarriesSecret: false,
      appStoresActivationSecret: false,
      supportSafe: true
    }' >"${path}"
}

main() {
  load_bootstrap_env
  parse_args "$@"
  validate_inputs

  log "Weave activation invite plan"
  log "- realm: ${TENANT_REALM}"
  log "- username: ${USERNAME}"
  log "- email: ${EMAIL}"
  log "- display name: ${DISPLAY_NAME}"
  log "- role: ${ROLE}"
  log "- group: ${WORKSPACE_GROUP}"
  log "- invite ref: ${INVITE_REF}"
  log "- activation mode: Keycloak required-action email"
  log "- required actions: ${REQUIRED_ACTIONS}"
  log "- action link lifetime: ${ACTIVATION_LIFESPAN_SECONDS}s"
  log "- QR/deeplink secrets: none"

  if [[ "${DRY_RUN}" == "true" ]]; then
    write_activation_evidence "${EVIDENCE_FILE}" "false"
    log "WEAVE_ACTIVATION_INVITE_DRY_RUN inviteRef=${INVITE_REF} supportSafe=true"
    log "Dry run only: Keycloak was not modified."
    return 0
  fi

  require_command curl
  require_command jq

  [[ -n "${TF_VAR_keycloak_admin_password:-}" ]] || fail "TF_VAR_keycloak_admin_password is required; run install.sh first or source .generated/bootstrap.env."

  local token
  token="$(admin_token)"
  [[ -n "${token}" ]] || fail "Could not obtain a Keycloak admin token for $(keycloak_public_url)."

  local base_url role_json group_id user_id
  base_url="$(keycloak_public_url)/admin/realms/${TENANT_REALM}"
  role_json="$(ensure_realm_role "${base_url}" "${token}" "${ROLE}")"
  group_id="$(ensure_group_id "${base_url}" "${token}" "${WORKSPACE_GROUP}")"
  user_id="$(upsert_user "${base_url}" "${token}")"

  curl_keycloak POST "${base_url}/users/${user_id}/role-mappings/realm" "${token}" "$(jq -n --argjson role "${role_json}" '[$role]')" >/dev/null
  curl_keycloak PUT "${base_url}/users/${user_id}/groups/${group_id}" "${token}" >/dev/null
  execute_activation_email "${base_url}" "${token}" "${user_id}"
  write_activation_evidence "${EVIDENCE_FILE}" "true"

  log "Activation invite created."
  log "- Keycloak sent a one-time required-action email for '${REQUIRED_ACTIONS}' with ${ACTIVATION_LIFESPAN_SECONDS}s lifetime."
  log "- Dogfood Mailpit should capture the message locally; do not copy the action URL into docs, QR codes, logs, or app storage."
  log "- After activation, the user should receive realm role '${ROLE}' plus group '${WORKSPACE_GROUP}'."
  log "- Verify through the backend facade with /api/me or the app first-run profile/status screen."
  [[ -n "${EVIDENCE_FILE}" ]] && log "- Support-safe evidence: ${EVIDENCE_FILE}"
  log "WEAVE_ACTIVATION_INVITE_CREATED inviteRef=${INVITE_REF} requiredActions=${REQUIRED_ACTIONS} lifespanSeconds=${ACTIVATION_LIFESPAN_SECONDS} supportSafe=true"
}

main "$@"
