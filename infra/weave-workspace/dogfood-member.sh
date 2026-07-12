#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
BOOTSTRAP_ENV_FILE="${ROOT_DIR}/.generated/bootstrap.env"
SUBJECT_FILE="${WEAVE_DOGFOOD_MEMBER_SUBJECT_FILE:-${ROOT_DIR}/.generated/dogfood-member.subject}"
REALM="${TF_VAR_tenant_slug:-weave}"
OPERATION=""
EVIDENCE_FILE=""
LIFESPAN_SECONDS="${WEAVE_DOGFOOD_MEMBER_ACTIVATION_LIFESPAN_SECONDS:-86400}"
CLIENT_ID="weave-app"
REDIRECT_URI="com.massimotter.weave:/oauthredirect"
EXPECTED_GROUPS="${WEAVE_DOGFOOD_MEMBER_GROUPS:-workspace-members,weave-board-editors,weave-calendar-editors}"

log() { printf '%s\n' "$*"; }
fail() { printf 'DOGFOOD_MEMBER_ERROR %s\n' "$*" >&2; exit 1; }

usage() {
  cat <<'EOF'
Usage: ./dogfood-member.sh status|ensure|resend-activation [options]

Manage the one persistent human dogfood member without overwriting it.

Required environment:
  WEAVE_DOGFOOD_MEMBER_USERNAME
  WEAVE_DOGFOOD_MEMBER_EMAIL
  WEAVE_DOGFOOD_MEMBER_DISPLAY_NAME

Options:
  --evidence-file PATH   Write support-safe JSON evidence.
  --subject-file PATH    Protected runtime file holding the immutable subject.
  --tenant-realm VALUE   Keycloak realm (default: TF_VAR_tenant_slug or weave).
  --groups CSV           Expected member/capability groups.
  --lifespan SECONDS     Initial/resend action-email lifetime (300..86400).
  -h, --help             Show this help.

The helper creates and configures an absent identity exactly once. Existing
pending and active identities are never updated. resend-activation only sends
mail for a pending identity. A recorded missing or changed subject fails closed.
EOF
}

load_environment() {
  local runtime_admin_username="${TF_VAR_keycloak_admin_username:-}"
  local runtime_admin_password="${TF_VAR_keycloak_admin_password:-}"
  if [[ -f "${BOOTSTRAP_ENV_FILE}" ]]; then
    # shellcheck disable=SC1090
    source "${BOOTSTRAP_ENV_FILE}"
  fi
  [[ -z "${runtime_admin_username}" ]] || TF_VAR_keycloak_admin_username="${runtime_admin_username}"
  [[ -z "${runtime_admin_password}" ]] || TF_VAR_keycloak_admin_password="${runtime_admin_password}"
}

parse_args() {
  [[ $# -gt 0 ]] || { usage >&2; exit 2; }
  OPERATION="$1"; shift
  case "${OPERATION}" in status|ensure|resend-activation) ;; *) fail "unknown operation '${OPERATION}'" ;; esac
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --evidence-file) EVIDENCE_FILE="${2:-}"; shift 2 ;;
      --subject-file) SUBJECT_FILE="${2:-}"; shift 2 ;;
      --tenant-realm) REALM="${2:-}"; shift 2 ;;
      --groups) EXPECTED_GROUPS="${2:-}"; shift 2 ;;
      --lifespan) LIFESPAN_SECONDS="${2:-}"; shift 2 ;;
      -h|--help) usage; exit 0 ;;
      *) fail "unknown argument '$1'" ;;
    esac
  done
}

validate_inputs() {
  : "${WEAVE_DOGFOOD_MEMBER_USERNAME:?WEAVE_DOGFOOD_MEMBER_USERNAME is required}"
  : "${WEAVE_DOGFOOD_MEMBER_EMAIL:?WEAVE_DOGFOOD_MEMBER_EMAIL is required}"
  : "${WEAVE_DOGFOOD_MEMBER_DISPLAY_NAME:?WEAVE_DOGFOOD_MEMBER_DISPLAY_NAME is required}"
  [[ "${WEAVE_DOGFOOD_MEMBER_USERNAME}" != "test" ]] || fail "persistent human member must not use the disposable automation username 'test'"
  [[ "${LIFESPAN_SECONDS}" =~ ^[0-9]+$ ]] || fail "lifespan must be seconds"
  (( LIFESPAN_SECONDS >= 300 && LIFESPAN_SECONDS <= 86400 )) || fail "lifespan must be between 300 and 86400 seconds"
  [[ -n "${EXPECTED_GROUPS}" ]] || fail "expected groups must not be empty"
}

public_port_suffix() {
  local scheme="${TF_VAR_public_scheme:-https}" port="${TF_VAR_proxy_host_port:-443}"
  if [[ "${scheme}:${port}" == "http:80" || "${scheme}:${port}" == "https:443" ]]; then printf ''; else printf ':%s' "${port}"; fi
}

keycloak_url() {
  printf '%s://%s.%s%s' "${TF_VAR_public_scheme:-https}" "${TF_VAR_auth_subdomain:-auth}" \
    "${TF_VAR_tenant_domain:-weave.test}" "$(public_port_suffix)"
}

curl_common() {
  local -a args=(--silent --show-error --fail-with-body)
  if [[ -n "${WEAVE_TLS_CA_FILE:-}" ]]; then args+=(--cacert "${WEAVE_TLS_CA_FILE}")
  elif [[ -n "${TF_VAR_caddy_tls_ca_file:-}" && -f "${TF_VAR_caddy_tls_ca_file}" ]]; then args+=(--cacert "${TF_VAR_caddy_tls_ca_file}"); fi
  printf '%s\0' "${args[@]}"
}

request() {
  local method="$1" url="$2" token="${3:-}" body="${4:-}" content_type="${5:-application/json}"
  local -a args=()
  while IFS= read -r -d '' arg; do args+=("${arg}"); done < <(curl_common)
  args+=(-X "${method}")
  [[ -n "${token}" ]] && args+=(-H "Authorization: Bearer ${token}")
  if [[ -n "${body}" ]]; then args+=(-H "Content-Type: ${content_type}" --data "${body}"); fi
  curl "${args[@]}" "${url}"
}

admin_token() {
  local -a args=()
  while IFS= read -r -d '' arg; do args+=("${arg}"); done < <(curl_common)
  curl "${args[@]}" -X POST "$(keycloak_url)/realms/master/protocol/openid-connect/token" \
    -H 'Content-Type: application/x-www-form-urlencoded' --data-urlencode 'client_id=admin-cli' \
    --data-urlencode "username=${TF_VAR_keycloak_admin_username:-admin}" \
    --data-urlencode "password=${TF_VAR_keycloak_admin_password:-}" --data-urlencode 'grant_type=password' |
    jq -r '.access_token // empty'
}

encode() { jq -nr --arg value "$1" '$value|@uri'; }
sha256() { printf '%s' "$1" | shasum -a 256 | awk '{print $1}'; }
api_base() { printf '%s/admin/realms/%s' "$(keycloak_url)" "$(encode "${REALM}")"; }

recorded_subject() { [[ -s "${SUBJECT_FILE}" ]] && tr -d '\r\n' <"${SUBJECT_FILE}" || true; }

record_subject_once() {
  local subject="$1" recorded
  recorded="$(recorded_subject)"
  if [[ -n "${recorded}" && "${recorded}" != "${subject}" ]]; then fail "identity_changed"; fi
  if [[ -z "${recorded}" ]]; then
    mkdir -p "$(dirname -- "${SUBJECT_FILE}")"
    chmod 700 "$(dirname -- "${SUBJECT_FILE}")"
    (umask 077; printf '%s\n' "${subject}" >"${SUBJECT_FILE}")
  fi
}

resolve_user() {
  local base="$1" token="$2" by_username by_email matches
  by_username="$(request GET "${base}/users?username=$(encode "${WEAVE_DOGFOOD_MEMBER_USERNAME}")&exact=true" "${token}")"
  by_email="$(request GET "${base}/users?email=$(encode "${WEAVE_DOGFOOD_MEMBER_EMAIL}")&exact=true" "${token}")"
  matches="$(jq -cn --argjson a "${by_username}" --argjson b "${by_email}" '$a + $b | unique_by(.id)')"
  [[ "$(jq 'length' <<<"${matches}")" -le 1 ]] || fail "configured identity is ambiguous"
  jq -c '.[0] // empty' <<<"${matches}"
}

state_for_user() {
  local user="$1"
  if [[ -z "${user}" ]]; then printf 'missing'; return; fi
  if [[ "$(jq -r '.enabled // false' <<<"${user}")" != true ]]; then printf 'disabled'; return; fi
  if [[ "$(jq -r '.emailVerified // false' <<<"${user}")" == true ]] && [[ "$(jq '.requiredActions // [] | length' <<<"${user}")" -eq 0 ]]; then printf 'active'; else printf 'pending'; fi
}

verify_subject_invariant() {
  local user="$1" recorded subject
  recorded="$(recorded_subject)"
  [[ -n "${recorded}" ]] || return 0
  [[ -n "${user}" ]] || fail "identity_missing"
  subject="$(jq -r '.id' <<<"${user}")"
  [[ "${subject}" == "${recorded}" ]] || fail "identity_changed"
}

find_named_id() {
  local json="$1" name="$2" type="$3" id
  id="$(jq -r --arg name "${name}" '.[] | select(.name == $name or .alias == $name) | .id' <<<"${json}")"
  [[ "$(wc -l <<<"${id}" | tr -d ' ')" -eq 1 && -n "${id}" ]] || fail "${type} '${name}' is unavailable or ambiguous"
  printf '%s' "${id}"
}

resolve_org_id() {
  local base="$1" token="$2" organizations
  organizations="$(request GET "${base}/organizations?search=$(encode "${REALM}")&exact=true" "${token}")"
  find_named_id "${organizations}" "${REALM}" organization
}

resolve_client_id() {
  local base="$1" token="$2" clients
  clients="$(request GET "${base}/clients?clientId=$(encode "${CLIENT_ID}")" "${token}")"
  find_named_id "${clients}" "${CLIENT_ID}" client
}

add_initial_access() {
  local base="$1" token="$2" subject="$3" org_id client_uuid role group group_id groups
  org_id="$(resolve_org_id "${base}" "${token}")"
  request POST "${base}/organizations/${org_id}/members" "${token}" "$(jq -cn --arg id "${subject}" '$id')" >/dev/null
  client_uuid="$(resolve_client_id "${base}" "${token}")"
  role="$(request GET "${base}/clients/${client_uuid}/roles/member" "${token}")"
  request POST "${base}/users/${subject}/role-mappings/clients/${client_uuid}" "${token}" "[$(jq -c . <<<"${role}")]" >/dev/null
  IFS=',' read -r -a groups <<<"${EXPECTED_GROUPS}"
  for group in "${groups[@]}"; do
    group="${group#"${group%%[![:space:]]*}"}"; group="${group%"${group##*[![:space:]]}"}"
    group_id="$(find_named_id "$(request GET "${base}/groups?search=$(encode "${group}")&exact=true" "${token}")" "${group}" group)"
    request PUT "${base}/users/${subject}/groups/${group_id}" "${token}" >/dev/null
  done
}

verify_active_access() {
  local base="$1" token="$2" subject="$3" org_id client_uuid roles memberships group expected groups
  org_id="$(resolve_org_id "${base}" "${token}")"
  request GET "${base}/organizations/${org_id}/members/${subject}" "${token}" >/dev/null || fail "active member lacks organization membership"
  client_uuid="$(resolve_client_id "${base}" "${token}")"
  roles="$(request GET "${base}/users/${subject}/role-mappings/clients/${client_uuid}" "${token}")"
  jq -e 'any(.name == "member")' <<<"${roles}" >/dev/null || fail "active member lacks member role"
  memberships="$(request GET "${base}/users/${subject}/groups" "${token}")"
  IFS=',' read -r -a groups <<<"${EXPECTED_GROUPS}"
  for expected in "${groups[@]}"; do
    expected="${expected#"${expected%%[![:space:]]*}"}"; expected="${expected%"${expected##*[![:space:]]}"}"
    jq -e --arg name "${expected}" 'any(.name == $name)' <<<"${memberships}" >/dev/null || fail "active member lacks expected group '${expected}'"
  done
}

create_once() {
  local base="$1" token="$2" first last payload user subject
  first="${WEAVE_DOGFOOD_MEMBER_DISPLAY_NAME%% *}"
  if [[ "${WEAVE_DOGFOOD_MEMBER_DISPLAY_NAME}" == *' '* ]]; then last="${WEAVE_DOGFOOD_MEMBER_DISPLAY_NAME#* }"; else last=""; fi
  payload="$(jq -n --arg username "${WEAVE_DOGFOOD_MEMBER_USERNAME}" --arg email "${WEAVE_DOGFOOD_MEMBER_EMAIL}" \
    --arg first "${first}" --arg last "${last}" '{username:$username,email:$email,firstName:$first,lastName:$last,enabled:true,emailVerified:false,requiredActions:["VERIFY_EMAIL","UPDATE_PASSWORD"]}')"
  request POST "${base}/users" "${token}" "${payload}" >/dev/null
  user="$(resolve_user "${base}" "${token}")"; [[ -n "${user}" ]] || fail "created identity could not be resolved"
  subject="$(jq -r '.id' <<<"${user}")"
  record_subject_once "${subject}"
  add_initial_access "${base}" "${token}" "${subject}"
  send_activation "${base}" "${token}" "${subject}"
}

send_activation() {
  local base="$1" token="$2" subject="$3"
  request PUT "${base}/users/${subject}/execute-actions-email?lifespan=${LIFESPAN_SECONDS}&client_id=$(encode "${CLIENT_ID}")&redirect_uri=$(encode "${REDIRECT_URI}")" \
    "${token}" '["VERIFY_EMAIL","UPDATE_PASSWORD"]' >/dev/null
}

write_evidence() {
  local state="$1" subject="$2" action="$3"
  [[ -n "${EVIDENCE_FILE}" ]] || return 0
  mkdir -p "$(dirname -- "${EVIDENCE_FILE}")"
  jq -n --arg state "${state}" --arg action "${action}" --arg realm "${REALM}" \
    --arg usernameSha256 "$(sha256 "${WEAVE_DOGFOOD_MEMBER_USERNAME}")" --arg emailSha256 "$(sha256 "${WEAVE_DOGFOOD_MEMBER_EMAIL}")" \
    --arg subjectSha256 "$([[ -n "${subject}" ]] && sha256 "${subject}" || true)" \
    --argjson mailSent "$([[ "${action}" == created_and_activation_sent || "${action}" == activation_resent ]] && printf true || printf false)" \
    '{schemaVersion:"weave.dogfood.persistent-member.v1",realm:$realm,state:$state,action:$action,usernameSha256:$usernameSha256,emailSha256:$emailSha256,subjectSha256:$subjectSha256,activation:{mode:"keycloak-required-actions-email",requiredActions:["VERIFY_EMAIL","UPDATE_PASSWORD"],mailSent:$mailSent},qrOrDeeplinkCarriesSecret:false,appStoresActivationSecret:false,supportSafe:true}' >"${EVIDENCE_FILE}"
}

main() {
  load_environment; parse_args "$@"; validate_inputs
  command -v curl >/dev/null || fail "curl is required"; command -v jq >/dev/null || fail "jq is required"
  [[ -n "${TF_VAR_keycloak_admin_password:-}" ]] || fail "TF_VAR_keycloak_admin_password is required"
  local token base user state subject="" action="none"
  token="$(admin_token)"; [[ -n "${token}" ]] || fail "Keycloak admin authentication failed"
  base="$(api_base)"; user="$(resolve_user "${base}" "${token}")"; verify_subject_invariant "${user}"
  state="$(state_for_user "${user}")"
  case "${OPERATION}:${state}" in
    ensure:missing) create_once "${base}" "${token}"; user="$(resolve_user "${base}" "${token}")"; state="pending"; action="created_and_activation_sent" ;;
    ensure:pending|ensure:active) action="unchanged" ;;
    ensure:disabled) fail "identity is disabled" ;;
    resend-activation:pending) subject="$(jq -r '.id' <<<"${user}")"; send_activation "${base}" "${token}" "${subject}"; action="activation_resent" ;;
    resend-activation:active) action="account_already_active" ;;
    resend-activation:missing) fail "identity_missing" ;;
    resend-activation:disabled) fail "identity is disabled" ;;
    status:*) action="observed" ;;
  esac
  [[ -n "${user}" ]] && subject="$(jq -r '.id' <<<"${user}")"
  [[ -n "${subject}" ]] && record_subject_once "${subject}"
  [[ "${state}" == active ]] && verify_active_access "${base}" "${token}" "${subject}"
  write_evidence "${state}" "${subject}" "${action}"
  log "DOGFOOD_MEMBER state=${state} action=${action} subjectSha256=$([[ -n "${subject}" ]] && sha256 "${subject}" || printf none) supportSafe=true"
}

main "$@"
