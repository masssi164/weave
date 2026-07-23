#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
BOOTSTRAP_ENV_FILE="${WEAVE_DOGFOOD_BOOTSTRAP_ENV:-${ROOT_DIR}/.generated/bootstrap.env}"
SUBJECT_FILE="${WEAVE_DOGFOOD_MEMBER_SUBJECT_FILE:-${ROOT_DIR}/.generated/dogfood-member.subject}"
REALM="${WEAVE_TENANT_SLUG:-weave}"
OPERATION=""
EVIDENCE_FILE=""
PRIOR_EVIDENCE_FILE=""
RECOVERY_APPROVAL_REF=""
RECOVERY_CONFIRMATION=""
BOOTSTRAP_RETIREMENT_CONFIRMATION=""
LIFESPAN_SECONDS="${WEAVE_DOGFOOD_MEMBER_ACTIVATION_LIFESPAN_SECONDS:-86400}"
CLIENT_ID="weave-app"
REDIRECT_URI="com.massimotter.weave:/oauthredirect"
EXPECTED_GROUPS="${WEAVE_DOGFOOD_MEMBER_GROUPS:-/weave/members,/weave-board-editors,/weave-calendar-editors}"
MAILPIT_VERIFY_TIMEOUT_SECONDS="${WEAVE_DOGFOOD_MEMBER_MAILPIT_VERIFY_TIMEOUT_SECONDS:-60}"
MAILPIT_EXPECTED_SUBJECT="${WEAVE_DOGFOOD_MEMBER_MAIL_SUBJECT:-Complete your Weave account setup}"
MAIL_MESSAGE_ID_SHA256=""
MAIL_VERIFIED_AT=""
RECOVERY_CONFIRMATION_LITERAL="retire-lost-pending-identity"
BOOTSTRAP_RETIREMENT_CONFIRMATION_LITERAL="retire-restored-test-bootstrap"
GROUP_ADOPTION_CONFIRMATION=""
GROUP_ADOPTION_CONFIRMATION_LITERAL="adopt-workspace-members-to-weave-members"
RESTORED_BOOTSTRAP_USERNAME="test"

log() { printf '%s\n' "$*"; }
fail() { printf 'DOGFOOD_MEMBER_ERROR %s\n' "$*" >&2; exit 1; }

usage() {
  cat <<'EOF'
Usage: ./dogfood-member.sh status|ensure|resend-activation|migrate-legacy-member-group|recover-lost-pending|retire-restored-bootstrap [options]

Manage the one persistent human dogfood member without overwriting it.

Required environment:
  WEAVE_DOGFOOD_MEMBER_USERNAME
  WEAVE_DOGFOOD_MEMBER_EMAIL
  WEAVE_DOGFOOD_MEMBER_DISPLAY_NAME

Options:
  --evidence-file PATH   Write support-safe JSON evidence.
  --subject-file PATH    Protected runtime file holding the immutable subject.
  --tenant-realm VALUE   Keycloak realm (default: WEAVE_TENANT_SLUG or weave).
  --groups CSV           Expected member/capability groups.
  --lifespan SECONDS     Initial/resend action-email lifetime (300..86400).
  --prior-evidence PATH  Last accepted support-safe member evidence (recovery only).
  --approval-ref URL     Protected GitHub Actions run URL (recovery only).
  --confirm-retirement VALUE
                         Must be 'retire-lost-pending-identity' (recovery only).
  --confirm-bootstrap-retirement VALUE
                         Must be 'retire-restored-test-bootstrap' (recovery only).
  --confirm-group-adoption VALUE
                         Must be 'adopt-workspace-members-to-weave-members'.
  -h, --help             Show this help.

The helper creates and configures an absent identity exactly once. Existing
pending and active identities are never updated. resend-activation only sends
mail for a pending identity. A recorded missing or changed subject fails closed.
recover-lost-pending is an explicit disaster-recovery exception for a proven
never-activated identity; it never applies to an active or ambiguous identity.
retire-restored-bootstrap removes only the exact disposable 'test' identity
from an older platform backup after proving no protected member is present.
EOF
}

load_environment() {
  local runtime_admin_username="${WEAVE_KEYCLOAK_ADMIN_USERNAME:-}"
  local runtime_admin_password="${WEAVE_KEYCLOAK_ADMIN_PASSWORD:-}"
  if [[ -f "${BOOTSTRAP_ENV_FILE}" ]]; then
    # shellcheck disable=SC1090
    source "${BOOTSTRAP_ENV_FILE}"
  fi
  [[ -z "${runtime_admin_username}" ]] || WEAVE_KEYCLOAK_ADMIN_USERNAME="${runtime_admin_username}"
  [[ -z "${runtime_admin_password}" ]] || WEAVE_KEYCLOAK_ADMIN_PASSWORD="${runtime_admin_password}"
}

parse_args() {
  [[ $# -gt 0 ]] || { usage >&2; exit 2; }
  OPERATION="$1"; shift
  case "${OPERATION}" in status|ensure|resend-activation|migrate-legacy-member-group|recover-lost-pending|retire-restored-bootstrap) ;; *) fail "unknown operation '${OPERATION}'" ;; esac
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --evidence-file) EVIDENCE_FILE="${2:-}"; shift 2 ;;
      --subject-file) SUBJECT_FILE="${2:-}"; shift 2 ;;
      --tenant-realm) REALM="${2:-}"; shift 2 ;;
      --groups) EXPECTED_GROUPS="${2:-}"; shift 2 ;;
      --lifespan) LIFESPAN_SECONDS="${2:-}"; shift 2 ;;
      --prior-evidence) PRIOR_EVIDENCE_FILE="${2:-}"; shift 2 ;;
      --approval-ref) RECOVERY_APPROVAL_REF="${2:-}"; shift 2 ;;
      --confirm-retirement) RECOVERY_CONFIRMATION="${2:-}"; shift 2 ;;
      --confirm-bootstrap-retirement) BOOTSTRAP_RETIREMENT_CONFIRMATION="${2:-}"; shift 2 ;;
      --confirm-group-adoption) GROUP_ADOPTION_CONFIRMATION="${2:-}"; shift 2 ;;
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
  [[ "${MAILPIT_VERIFY_TIMEOUT_SECONDS}" =~ ^[0-9]+$ ]] || fail "Mailpit verification timeout must be seconds"
  (( MAILPIT_VERIFY_TIMEOUT_SECONDS >= 1 && MAILPIT_VERIFY_TIMEOUT_SECONDS <= 300 )) || fail "Mailpit verification timeout must be between 1 and 300 seconds"
  [[ -n "${EXPECTED_GROUPS}" ]] || fail "expected groups must not be empty"
  if [[ "${OPERATION}" == recover-lost-pending || "${OPERATION}" == retire-restored-bootstrap ]]; then
    [[ -n "${EVIDENCE_FILE}" ]] || fail "${OPERATION} requires --evidence-file"
    [[ -s "${PRIOR_EVIDENCE_FILE}" ]] || fail "${OPERATION} requires existing --prior-evidence"
    [[ "${RECOVERY_APPROVAL_REF}" =~ ^https://github\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/actions/runs/[0-9]+$ ]] ||
      fail "${OPERATION} requires a support-safe protected GitHub Actions run URL"
  fi
  if [[ "${OPERATION}" == recover-lost-pending ]]; then
    [[ "${RECOVERY_CONFIRMATION}" == "${RECOVERY_CONFIRMATION_LITERAL}" ]] || fail "recover-lost-pending requires the exact retirement confirmation"
  fi
  if [[ "${OPERATION}" == retire-restored-bootstrap ]]; then
    [[ "${BOOTSTRAP_RETIREMENT_CONFIRMATION}" == "${BOOTSTRAP_RETIREMENT_CONFIRMATION_LITERAL}" ]] ||
      fail "retire-restored-bootstrap requires the exact bootstrap retirement confirmation"
  fi
  if [[ "${OPERATION}" == migrate-legacy-member-group ]]; then
    [[ -n "${EVIDENCE_FILE}" ]] || fail "migrate-legacy-member-group requires --evidence-file"
    [[ "${RECOVERY_APPROVAL_REF}" =~ ^https://github\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/actions/runs/[0-9]+$ ]] ||
      fail "migrate-legacy-member-group requires a support-safe protected GitHub Actions run URL"
    [[ "${GROUP_ADOPTION_CONFIRMATION}" == "${GROUP_ADOPTION_CONFIRMATION_LITERAL}" ]] ||
      fail "migrate-legacy-member-group requires the exact group-adoption confirmation"
  fi
}

public_port_suffix() {
  local scheme="${WEAVE_PUBLIC_SCHEME:-https}" port="${WEAVE_PROXY_HTTPS_HOST_PORT:-443}"
  if [[ "${scheme}:${port}" == "http:80" || "${scheme}:${port}" == "https:443" ]]; then printf ''; else printf ':%s' "${port}"; fi
}

keycloak_url() {
  printf '%s://%s.%s%s' "${WEAVE_PUBLIC_SCHEME:-https}" "${WEAVE_AUTH_SUBDOMAIN:-auth}" \
    "${WEAVE_TENANT_DOMAIN:-weave.test}" "$(public_port_suffix)"
}

mailpit_messages_url() {
  printf '%s://mail.%s%s/api/v1/messages' "${WEAVE_PUBLIC_SCHEME:-https}" \
    "${WEAVE_TENANT_DOMAIN:-weave.test}" "$(public_port_suffix)"
}

curl_common() {
  local -a args=(--silent --show-error --fail-with-body)
  if [[ -n "${WEAVE_TLS_CA_FILE:-}" ]]; then args+=(--cacert "${WEAVE_TLS_CA_FILE}")
  elif [[ -n "${WEAVE_CADDY_TLS_CA_FILE:-}" && -f "${WEAVE_CADDY_TLS_CA_FILE}" ]]; then args+=(--cacert "${WEAVE_CADDY_TLS_CA_FILE}"); fi
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

request_http_status() {
  local method="$1" url="$2" token="${3:-}" http_status
  local -a args=(--silent --show-error --output /dev/null --write-out '%{http_code}' -X "${method}")
  if [[ -n "${WEAVE_TLS_CA_FILE:-}" ]]; then args+=(--cacert "${WEAVE_TLS_CA_FILE}")
  elif [[ -n "${WEAVE_CADDY_TLS_CA_FILE:-}" && -f "${WEAVE_CADDY_TLS_CA_FILE}" ]]; then args+=(--cacert "${WEAVE_CADDY_TLS_CA_FILE}"); fi
  [[ -n "${token}" ]] && args+=(-H "Authorization: Bearer ${token}")
  if ! http_status="$(curl "${args[@]}" "${url}")"; then
    fail "Keycloak subject absence could not be verified"
  fi
  [[ "${http_status}" =~ ^[0-9]{3}$ ]] || fail "Keycloak returned an invalid HTTP status"
  printf '%s' "${http_status}"
}

admin_token() {
  local -a args=()
  while IFS= read -r -d '' arg; do args+=("${arg}"); done < <(curl_common)
  curl "${args[@]}" -X POST "$(keycloak_url)/realms/master/protocol/openid-connect/token" \
    -H 'Content-Type: application/x-www-form-urlencoded' --data-urlencode 'client_id=admin-cli' \
    --data-urlencode "username=${WEAVE_KEYCLOAK_ADMIN_USERNAME:-admin}" \
    --data-urlencode "password=${WEAVE_KEYCLOAK_ADMIN_PASSWORD:-}" --data-urlencode 'grant_type=password' |
    jq -r '.access_token // empty'
}

encode() { jq -nr --arg value "$1" '$value|@uri'; }
sha256() { printf '%s' "$1" | shasum -a 256 | awk '{print $1}'; }
api_base() { printf '%s/admin/realms/%s' "$(keycloak_url)" "$(encode "${REALM}")"; }

recorded_subject() { [[ -s "${SUBJECT_FILE}" ]] && tr -d '\r\n' <"${SUBJECT_FILE}" || true; }

matching_mail_ids() {
  local payload="$1" expected_hash address normalized_address
  expected_hash="$(printf '%s' "${WEAVE_DOGFOOD_MEMBER_EMAIL}" | tr '[:upper:]' '[:lower:]' | shasum -a 256 | awk '{print $1}')"
  jq -r --arg subject "${MAILPIT_EXPECTED_SUBJECT}" \
    '.messages[]? | select((.Subject // .subject // "") == $subject) | [(.ID // .Id // .id // ""), ((.To // .to // []) | .. | objects | (.Address // .address // .Email // .email // empty))] | @tsv' \
    <<<"${payload}" | while IFS=$'\t' read -r id address; do
      [[ -n "${id}" && -n "${address}" ]] || continue
      normalized_address="$(printf '%s' "${address}" | tr '[:upper:]' '[:lower:]')"
      [[ "$(sha256 "${normalized_address}")" == "${expected_hash}" ]] && printf '%s\n' "${id}"
    done
}

mailpit_snapshot() {
  local payload
  payload="$(request GET "$(mailpit_messages_url)")" || fail "Mailpit HTTPS API is not ready"
  matching_mail_ids "${payload}"
}

verify_new_mail_visible() {
  local before_ids="$1" deadline payload id
  deadline=$((SECONDS + MAILPIT_VERIFY_TIMEOUT_SECONDS))
  while (( SECONDS <= deadline )); do
    payload="$(request GET "$(mailpit_messages_url)" 2>/dev/null || true)"
    if [[ -n "${payload}" ]]; then
      while IFS= read -r id; do
        [[ -n "${id}" ]] || continue
        if ! grep -Fxq -- "${id}" <<<"${before_ids}"; then
          MAIL_MESSAGE_ID_SHA256="$(sha256 "${id}")"
          MAIL_VERIFIED_AT="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
          return 0
        fi
      done < <(matching_mail_ids "${payload}")
    fi
    sleep 2
  done
  return 1
}

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

find_group_path_id() {
  local base="$1" token="$2" path="$3" leaf groups id
  [[ "${path}" == /* && "${path}" != */ ]] || fail "group path '${path}' is not canonical"
  leaf="${path##*/}"
  groups="$(request GET "${base}/groups?search=$(encode "${leaf}")&exact=true&briefRepresentation=false" "${token}")"
  id="$(jq -r --arg path "${path}" '[.[] | select(.path == $path) | .id] | if length == 1 then .[0] else empty end' <<<"${groups}")"
  [[ -n "${id}" ]] || fail "group path '${path}' is unavailable or ambiguous"
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
  org_id="$(resolve_org_id "${base}" "${token}")" || return 1
  request POST "${base}/organizations/${org_id}/members" "${token}" "$(jq -cn --arg id "${subject}" '$id')" >/dev/null || return 1
  client_uuid="$(resolve_client_id "${base}" "${token}")" || return 1
  role="$(request GET "${base}/clients/${client_uuid}/roles/member" "${token}")" || return 1
  request POST "${base}/users/${subject}/role-mappings/clients/${client_uuid}" "${token}" "[$(jq -c . <<<"${role}")]" >/dev/null || return 1
  IFS=',' read -r -a groups <<<"${EXPECTED_GROUPS}"
  for group in "${groups[@]}"; do
    group="${group#"${group%%[![:space:]]*}"}"; group="${group%"${group##*[![:space:]]}"}"
    group_id="$(find_group_path_id "${base}" "${token}" "${group}")" || return 1
    request PUT "${base}/users/${subject}/groups/${group_id}" "${token}" >/dev/null || return 1
  done
}

verify_expected_access() {
  local base="$1" token="$2" subject="$3" org_id client_uuid roles memberships group expected groups
  org_id="$(resolve_org_id "${base}" "${token}")"
  request GET "${base}/organizations/${org_id}/members/${subject}" "${token}" >/dev/null || fail "persistent member lacks organization membership"
  client_uuid="$(resolve_client_id "${base}" "${token}")"
  roles="$(request GET "${base}/users/${subject}/role-mappings/clients/${client_uuid}" "${token}")"
  jq -e 'any(.name == "member")' <<<"${roles}" >/dev/null || fail "persistent member lacks member role"
  memberships="$(request GET "${base}/users/${subject}/groups" "${token}")"
  IFS=',' read -r -a groups <<<"${EXPECTED_GROUPS}"
  for expected in "${groups[@]}"; do
    expected="${expected#"${expected%%[![:space:]]*}"}"; expected="${expected%"${expected##*[![:space:]]}"}"
    jq -e --arg path "${expected}" 'any(.path == $path)' <<<"${memberships}" >/dev/null ||
      fail "persistent member lacks expected group path '${expected}'; use the explicit protected adoption operation for legacy /workspace-members membership"
  done
}

migrate_legacy_member_group() {
  local base="$1" token="$2" user="$3" subject memberships legacy_id target_id temporary
  [[ -n "${user}" ]] || fail "legacy group adoption requires the configured identity"
  subject="$(jq -r '.id // empty' <<<"${user}")"
  [[ -n "${subject}" ]] || fail "legacy group adoption could not resolve the member subject"
  verify_subject_invariant "${user}"
  memberships="$(request GET "${base}/users/${subject}/groups?briefRepresentation=false&max=100" "${token}")"
  jq -e 'any(.path == "/workspace-members")' <<<"${memberships}" >/dev/null ||
    fail "legacy /workspace-members membership is not present"
  jq -e 'any(.path == "/weave/members") | not' <<<"${memberships}" >/dev/null ||
    fail "canonical /weave/members membership is already present; no adoption mutation is allowed"
  legacy_id="$(find_group_path_id "${base}" "${token}" /workspace-members)"
  target_id="$(find_group_path_id "${base}" "${token}" /weave/members)"
  request PUT "${base}/users/${subject}/groups/${target_id}" "${token}" >/dev/null
  request DELETE "${base}/users/${subject}/groups/${legacy_id}" "${token}" >/dev/null || {
    request DELETE "${base}/users/${subject}/groups/${target_id}" "${token}" >/dev/null 2>&1 || true
    fail "legacy group detachment failed; canonical membership was rolled back"
  }
  memberships="$(request GET "${base}/users/${subject}/groups?briefRepresentation=false&max=100" "${token}")"
  jq -e 'any(.path == "/weave/members") and (any(.path == "/workspace-members") | not)' <<<"${memberships}" >/dev/null ||
    fail "legacy group adoption did not converge"
  temporary="${EVIDENCE_FILE}.tmp.$$"
  umask 077
  jq -n \
    --arg subjectSha256 "$(sha256 "${subject}")" \
    --arg approvalRef "${RECOVERY_APPROVAL_REF}" \
    --arg adoptedAt "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" \
    '{schemaVersion:"weave.dogfood.member-group-adoption.v1",subjectSha256:$subjectSha256,approvalRef:$approvalRef,fromPath:"/workspace-members",toPath:"/weave/members",targetMembershipVerified:true,legacyMembershipRemoved:true,capabilityGroupsUnchanged:true,runtimeGroupGranted:false,adoptedAt:$adoptedAt,supportSafe:true}' >"${temporary}"
  chmod 600 "${temporary}"
  mv "${temporary}" "${EVIDENCE_FILE}"
  log "DOGFOOD_MEMBER_GROUP_ADOPTION from=/workspace-members to=/weave/members runtimeGroupGranted=false supportSafe=true"
}

create_pending_user() {
  local base="$1" token="$2" first last payload user
  first="${WEAVE_DOGFOOD_MEMBER_DISPLAY_NAME%% *}"
  if [[ "${WEAVE_DOGFOOD_MEMBER_DISPLAY_NAME}" == *' '* ]]; then last="${WEAVE_DOGFOOD_MEMBER_DISPLAY_NAME#* }"; else last=""; fi
  payload="$(jq -n --arg username "${WEAVE_DOGFOOD_MEMBER_USERNAME}" --arg email "${WEAVE_DOGFOOD_MEMBER_EMAIL}" \
    --arg first "${first}" --arg last "${last}" '{username:$username,email:$email,firstName:$first,lastName:$last,enabled:true,emailVerified:false,requiredActions:["VERIFY_EMAIL","UPDATE_PASSWORD"]}')"
  request POST "${base}/users" "${token}" "${payload}" >/dev/null
  user="$(resolve_user "${base}" "${token}")"; [[ -n "${user}" ]] || fail "created identity could not be resolved"
  printf '%s' "${user}"
}

create_once() {
  local base="$1" token="$2" user subject
  user="$(create_pending_user "${base}" "${token}")"
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

validate_pending_recovery_evidence() {
  local recorded="$1" recorded_hash evidence_candidate evidence_dir
  recorded_hash="$(sha256 "${recorded}")"
  jq -e --arg subjectSha256 "${recorded_hash}" '
    (.schemaVersion == "weave.dogfood.persistent-member.v1" or
      .schemaVersion == "weave.dogfood.persistent-member-recovery.v1") and
    .state == "pending" and
    .subjectSha256 == $subjectSha256 and
    .supportSafe == true and
    .activation.mode == "keycloak-required-actions-email" and
    .activation.mailSent == true and
    .activation.mailVisible == true and
    (.activation.requiredActions | index("VERIFY_EMAIL") != null) and
    (.activation.requiredActions | index("UPDATE_PASSWORD") != null)
  ' "${PRIOR_EVIDENCE_FILE}" >/dev/null || fail "prior evidence does not prove the recorded identity remained pending"

  evidence_dir="$(dirname -- "${PRIOR_EVIDENCE_FILE}")"
  while IFS= read -r evidence_candidate; do
    if jq -e --arg subjectSha256 "${recorded_hash}" '
      .supportSafe == true and .subjectSha256 == $subjectSha256 and .state == "active"
    ' "${evidence_candidate}" >/dev/null 2>&1; then
      fail "active evidence exists for the recorded identity"
    fi
  done < <(find "${evidence_dir}" -maxdepth 2 -type f -name '*.json' -print)
}

verify_recorded_subject_absent() {
  local base="$1" token="$2" recorded="$3" http_status
  http_status="$(request_http_status GET "${base}/users/${recorded}" "${token}")"
  case "${http_status}" in
    404) return 0 ;;
    200) fail "recorded pending subject still exists in Keycloak" ;;
    *) fail "recorded subject absence could not be proven (HTTP ${http_status})" ;;
  esac
}

delete_recovery_user() {
  local base="$1" token="$2" subject="$3"
  request DELETE "${base}/users/${subject}" "${token}" >/dev/null 2>&1 || true
}

write_recovery_evidence() {
  local output="$1" previous_subject="$2" replacement_subject="$3"
  mkdir -p "$(dirname -- "${output}")" || return 1
  if ! jq -n --arg realm "${REALM}" \
    --arg previousSubjectSha256 "$(sha256 "${previous_subject}")" \
    --arg subjectSha256 "$(sha256 "${replacement_subject}")" \
    --arg usernameSha256 "$(sha256 "${WEAVE_DOGFOOD_MEMBER_USERNAME}")" \
    --arg emailSha256 "$(sha256 "${WEAVE_DOGFOOD_MEMBER_EMAIL}")" \
    --arg approvalRef "${RECOVERY_APPROVAL_REF}" \
    --arg recoveredAt "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" \
    --arg mailMessageIdSha256 "${MAIL_MESSAGE_ID_SHA256}" \
    --arg mailVerifiedAt "${MAIL_VERIFIED_AT}" '
    {
      schemaVersion:"weave.dogfood.persistent-member-recovery.v1",
      realm:$realm,
      state:"pending",
      action:"lost_pending_identity_retired_and_recreated",
      reason:"keycloak-runtime-lost-no-identity-restorable-database-backup",
      previousSubjectSha256:$previousSubjectSha256,
      subjectSha256:$subjectSha256,
      usernameSha256:$usernameSha256,
      emailSha256:$emailSha256,
      approvalRef:$approvalRef,
      recoveredAt:$recoveredAt,
      retiredSubjectArchivedPrivately:true,
      activation:{
        mode:"keycloak-required-actions-email",
        requiredActions:["VERIFY_EMAIL","UPDATE_PASSWORD"],
        mailSent:true,
        mailVisible:($mailMessageIdSha256 != ""),
        messageIdSha256:$mailMessageIdSha256,
        verifiedAt:$mailVerifiedAt
      },
      readiness:{
        blocked:true,
        requiredGates:["private-backup","restore-smoke","repeat-deployment","activation","member-verification"]
      },
      supportSafe:true
    }
  ' >"${output}"; then
    rm -f -- "${output}" >/dev/null 2>&1 || true
    return 1
  fi
  if ! chmod 600 "${output}"; then
    rm -f -- "${output}" >/dev/null 2>&1 || true
    return 1
  fi
}

realm_human_users() {
  local base="$1" token="$2" users
  users="$(request GET "${base}/users?first=0&max=1000" "${token}")"
  jq -c '[.[] | select((.serviceAccountClientId // "") == "")]' <<<"${users}"
}

write_bootstrap_retirement_evidence() {
  local output="$1" recorded_subject="$2" bootstrap_subject="$3"
  mkdir -p "$(dirname -- "${output}")" || return 1
  if ! jq -n \
    --arg realm "${REALM}" \
    --arg recordedSubjectSha256 "$(sha256 "${recorded_subject}")" \
    --arg retiredSubjectSha256 "$(sha256 "${bootstrap_subject}")" \
    --arg retiredUsernameSha256 "$(sha256 "${RESTORED_BOOTSTRAP_USERNAME}")" \
    --arg protectedUsernameSha256 "$(sha256 "${WEAVE_DOGFOOD_MEMBER_USERNAME}")" \
    --arg approvalRef "${RECOVERY_APPROVAL_REF}" \
    --arg retiredAt "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" '
    {
      schemaVersion:"weave.dogfood.restored-bootstrap-retirement.v1",
      realm:$realm,
      action:"restored_disposable_bootstrap_retired",
      reason:"platform-backup-predates-recorded-protected-member",
      recordedSubjectSha256:$recordedSubjectSha256,
      retiredSubjectSha256:$retiredSubjectSha256,
      retiredUsernameSha256:$retiredUsernameSha256,
      protectedUsernameSha256:$protectedUsernameSha256,
      protectedIdentityPresentBefore:false,
      humanIdentityCountBefore:1,
      humanIdentityCountAfter:0,
      deletionBoundary:"keycloak-admin-api-exact-subject",
      approvalRef:$approvalRef,
      retiredAt:$retiredAt,
      supportSafe:true
    }
  ' >"${output}"; then
    rm -f -- "${output}" >/dev/null 2>&1 || true
    return 1
  fi
  chmod 600 "${output}" || {
    rm -f -- "${output}" >/dev/null 2>&1 || true
    return 1
  }
}

write_empty_bootstrap_boundary_evidence() {
  local output="$1" recorded_subject="$2"
  mkdir -p "$(dirname -- "${output}")" || return 1
  if ! jq -n \
    --arg realm "${REALM}" \
    --arg recordedSubjectSha256 "$(sha256 "${recorded_subject}")" \
    --arg protectedUsernameSha256 "$(sha256 "${WEAVE_DOGFOOD_MEMBER_USERNAME}")" \
    --arg approvalRef "${RECOVERY_APPROVAL_REF}" \
    --arg observedAt "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" '
    {
      schemaVersion:"weave.dogfood.restored-bootstrap-retirement.v1",
      realm:$realm,
      action:"not_required_empty_human_boundary",
      reason:"platform-backup-has-no-human-identity",
      recordedSubjectSha256:$recordedSubjectSha256,
      protectedUsernameSha256:$protectedUsernameSha256,
      protectedIdentityPresentBefore:false,
      humanIdentityCountBefore:0,
      humanIdentityCountAfter:0,
      deletionBoundary:"none",
      providerMutationPerformed:false,
      approvalRef:$approvalRef,
      observedAt:$observedAt,
      supportSafe:true
    }
  ' >"${output}"; then
    rm -f -- "${output}" >/dev/null 2>&1 || true
    return 1
  fi
  chmod 600 "${output}" || {
    rm -f -- "${output}" >/dev/null 2>&1 || true
    return 1
  }
}

retire_restored_bootstrap() {
  local base="$1" token="$2" current_user="$3" recorded humans bootstrap_user bootstrap_subject remaining
  local evidence_temporary
  recorded="$(recorded_subject)"
  [[ -n "${recorded}" ]] || fail "retire-restored-bootstrap requires a recorded subject"
  [[ -z "${current_user}" ]] || fail "retire-restored-bootstrap requires the configured identity to be absent"
  validate_pending_recovery_evidence "${recorded}"
  verify_recorded_subject_absent "${base}" "${token}" "${recorded}"

  humans="$(realm_human_users "${base}" "${token}")"
  if [[ "$(jq 'length' <<<"${humans}")" -eq 0 ]]; then
    evidence_temporary="${EVIDENCE_FILE}.tmp.$$"
    if ! (umask 077; write_empty_bootstrap_boundary_evidence "${evidence_temporary}" "${recorded}"); then
      rm -f -- "${evidence_temporary}" >/dev/null 2>&1 || true
      fail "support-safe empty bootstrap boundary evidence could not be prepared"
    fi
    mv "${evidence_temporary}" "${EVIDENCE_FILE}"
    log "DOGFOOD_MEMBER_RECOVERY action=not_required_empty_human_boundary humanIdentityCountAfter=0 providerMutationPerformed=false supportSafe=true"
    return
  fi
  [[ "$(jq 'length' <<<"${humans}")" -eq 1 ]] ||
    fail "restored realm must be empty or contain exactly one non-service identity before bootstrap retirement"
  bootstrap_user="$(jq -c --arg username "${RESTORED_BOOTSTRAP_USERNAME}" '[.[] | select(.username == $username)][0] // empty' <<<"${humans}")"
  [[ -n "${bootstrap_user}" ]] || fail "the sole restored identity is not the disposable bootstrap user"
  bootstrap_subject="$(jq -r '.id // empty' <<<"${bootstrap_user}")"
  [[ -n "${bootstrap_subject}" && "${bootstrap_subject}" != "${recorded}" ]] ||
    fail "restored bootstrap identity subject is missing or conflicts with the recorded member"

  evidence_temporary="${EVIDENCE_FILE}.tmp.$$"
  if ! (umask 077; write_bootstrap_retirement_evidence "${evidence_temporary}" "${recorded}" "${bootstrap_subject}"); then
    rm -f -- "${evidence_temporary}" >/dev/null 2>&1 || true
    fail "support-safe bootstrap retirement evidence could not be prepared"
  fi
  if ! request DELETE "${base}/users/${bootstrap_subject}" "${token}" >/dev/null; then
    rm -f -- "${evidence_temporary}" >/dev/null 2>&1 || true
    fail "restored bootstrap identity could not be retired through Keycloak"
  fi
  remaining="$(realm_human_users "${base}" "${token}")"
  if [[ "$(jq 'length' <<<"${remaining}")" -ne 0 ]] || [[ -n "$(resolve_user "${base}" "${token}")" ]]; then
    rm -f -- "${evidence_temporary}" >/dev/null 2>&1 || true
    fail "restored realm identity cleanup did not reach the exact empty human boundary"
  fi
  mv "${evidence_temporary}" "${EVIDENCE_FILE}"
  log "DOGFOOD_MEMBER_RECOVERY action=restored_disposable_bootstrap_retired retiredSubjectSha256=$(sha256 "${bootstrap_subject}") humanIdentityCountAfter=0 supportSafe=true"
}

archive_and_replace_subject() {
  local previous_subject="$1" replacement_subject="$2" parent archive_dir timestamp archive_file temporary
  parent="$(dirname -- "${SUBJECT_FILE}")"
  archive_dir="${parent}/retired-pending-identities"
  timestamp="$(date -u '+%Y%m%dT%H%M%SZ')"
  archive_file="${archive_dir}/${timestamp}-$(sha256 "${previous_subject}").subject"
  temporary="${SUBJECT_FILE}.tmp.$$"

  mkdir -p "${parent}" "${archive_dir}" || return 1
  chmod 700 "${parent}" "${archive_dir}" || return 1
  [[ ! -e "${archive_file}" ]] || return 1
  if ! (
    umask 077
    printf '%s\n' "${previous_subject}" >"${archive_file}"
    printf '%s\n' "${replacement_subject}" >"${temporary}"
  ); then
    rm -f -- "${archive_file}" "${temporary}" >/dev/null 2>&1 || true
    return 1
  fi
  if ! chmod 600 "${archive_file}" "${temporary}" ||
    ! mv "${temporary}" "${SUBJECT_FILE}"; then
    rm -f -- "${archive_file}" "${temporary}" >/dev/null 2>&1 || true
    return 1
  fi
}

recover_lost_pending() {
  local base="$1" token="$2" current_user="$3" previous_subject replacement_user replacement_subject
  local mail_before evidence_temporary
  previous_subject="$(recorded_subject)"
  [[ -n "${previous_subject}" ]] || fail "recover-lost-pending requires a recorded subject"
  [[ -z "${current_user}" ]] || fail "recover-lost-pending requires the configured identity to be absent"
  validate_pending_recovery_evidence "${previous_subject}"
  verify_recorded_subject_absent "${base}" "${token}" "${previous_subject}"

  mail_before="$(mailpit_snapshot)"
  replacement_user="$(create_pending_user "${base}" "${token}")"
  replacement_subject="$(jq -r '.id // empty' <<<"${replacement_user}")"
  [[ -n "${replacement_subject}" && "${replacement_subject}" != "${previous_subject}" ]] || {
    [[ -z "${replacement_subject}" ]] || delete_recovery_user "${base}" "${token}" "${replacement_subject}"
    fail "replacement identity did not receive a distinct Keycloak subject"
  }

  if ! (add_initial_access "${base}" "${token}" "${replacement_subject}" &&
    verify_expected_access "${base}" "${token}" "${replacement_subject}") >/dev/null 2>&1; then
    delete_recovery_user "${base}" "${token}" "${replacement_subject}"
    fail "replacement identity access provisioning failed"
  fi
  if ! send_activation "${base}" "${token}" "${replacement_subject}"; then
    delete_recovery_user "${base}" "${token}" "${replacement_subject}"
    fail "replacement identity activation request failed"
  fi
  if ! verify_new_mail_visible "${mail_before}"; then
    delete_recovery_user "${base}" "${token}" "${replacement_subject}"
    fail "activation mail was not visible through the Mailpit HTTPS API within ${MAILPIT_VERIFY_TIMEOUT_SECONDS}s"
  fi

  evidence_temporary="${EVIDENCE_FILE}.tmp.$$"
  if ! (umask 077; write_recovery_evidence "${evidence_temporary}" "${previous_subject}" "${replacement_subject}"); then
    rm -f -- "${evidence_temporary}" >/dev/null 2>&1 || true
    delete_recovery_user "${base}" "${token}" "${replacement_subject}"
    fail "support-safe replacement transition evidence could not be prepared"
  fi
  if ! archive_and_replace_subject "${previous_subject}" "${replacement_subject}"; then
    rm -f -- "${evidence_temporary}" >/dev/null 2>&1 || true
    delete_recovery_user "${base}" "${token}" "${replacement_subject}"
    fail "private subject retirement archive could not be committed"
  fi
  mv "${evidence_temporary}" "${EVIDENCE_FILE}"
  log "DOGFOOD_MEMBER_RECOVERY state=pending action=lost_pending_identity_retired_and_recreated previousSubjectSha256=$(sha256 "${previous_subject}") subjectSha256=$(sha256 "${replacement_subject}") readinessBlocked=true supportSafe=true"
}

write_evidence() {
  local state="$1" subject="$2" action="$3"
  [[ -n "${EVIDENCE_FILE}" ]] || return 0
  mkdir -p "$(dirname -- "${EVIDENCE_FILE}")"
  jq -n --arg state "${state}" --arg action "${action}" --arg realm "${REALM}" \
    --arg usernameSha256 "$(sha256 "${WEAVE_DOGFOOD_MEMBER_USERNAME}")" --arg emailSha256 "$(sha256 "${WEAVE_DOGFOOD_MEMBER_EMAIL}")" \
    --arg subjectSha256 "$([[ -n "${subject}" ]] && sha256 "${subject}" || true)" \
    --arg mailMessageIdSha256 "${MAIL_MESSAGE_ID_SHA256}" --arg mailVerifiedAt "${MAIL_VERIFIED_AT}" \
    --argjson mailSent "$([[ "${action}" == created_and_activation_sent || "${action}" == activation_resent ]] && printf true || printf false)" \
    '{schemaVersion:"weave.dogfood.persistent-member.v1",realm:$realm,state:$state,action:$action,usernameSha256:$usernameSha256,emailSha256:$emailSha256,subjectSha256:$subjectSha256,activation:{mode:"keycloak-required-actions-email",requiredActions:["VERIFY_EMAIL","UPDATE_PASSWORD"],mailSent:$mailSent,mailVisible:($mailMessageIdSha256 != ""),messageIdSha256:$mailMessageIdSha256,verifiedAt:$mailVerifiedAt},qrOrDeeplinkCarriesSecret:false,appStoresActivationSecret:false,supportSafe:true}' >"${EVIDENCE_FILE}"
}

main() {
  load_environment; parse_args "$@"; validate_inputs
  command -v curl >/dev/null || fail "curl is required"; command -v jq >/dev/null || fail "jq is required"
  [[ -n "${WEAVE_KEYCLOAK_ADMIN_PASSWORD:-}" ]] || fail "WEAVE_KEYCLOAK_ADMIN_PASSWORD is required"
  local token base user state subject="" action="none" mail_before=""
  token="$(admin_token)"; [[ -n "${token}" ]] || fail "Keycloak admin authentication failed"
  base="$(api_base)"; user="$(resolve_user "${base}" "${token}")"
  if [[ "${OPERATION}" == retire-restored-bootstrap ]]; then
    retire_restored_bootstrap "${base}" "${token}" "${user}"
    return
  fi
  if [[ "${OPERATION}" == recover-lost-pending ]]; then
    recover_lost_pending "${base}" "${token}" "${user}"
    return
  fi
  if [[ "${OPERATION}" == migrate-legacy-member-group ]]; then
    migrate_legacy_member_group "${base}" "${token}" "${user}"
    return
  fi
  verify_subject_invariant "${user}"
  state="$(state_for_user "${user}")"
  if [[ "${OPERATION}:${state}" == ensure:missing || "${OPERATION}:${state}" == resend-activation:pending ]]; then
    mail_before="$(mailpit_snapshot)"
  fi
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
  if [[ "${action}" == created_and_activation_sent || "${action}" == activation_resent ]]; then
    verify_new_mail_visible "${mail_before}" ||
      fail "activation mail was not visible through the Mailpit HTTPS API within ${MAILPIT_VERIFY_TIMEOUT_SECONDS}s"
  fi
  [[ -n "${user}" ]] && subject="$(jq -r '.id' <<<"${user}")"
  [[ -n "${subject}" ]] && record_subject_once "${subject}"
  [[ "${state}" == active ]] && verify_expected_access "${base}" "${token}" "${subject}"
  write_evidence "${state}" "${subject}" "${action}"
  log "DOGFOOD_MEMBER state=${state} action=${action} subjectSha256=$([[ -n "${subject}" ]] && sha256 "${subject}" || printf none) supportSafe=true"
}

main "$@"
