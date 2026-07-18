#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly ROOT_DIR
readonly BOOTSTRAP_ENV_FILE="${WEAVE_DOGFOOD_BOOTSTRAP_ENV:-${ROOT_DIR}/.generated/bootstrap.env}"
readonly DOGFOOD_MEMBER_SCRIPT="${ROOT_DIR}/dogfood-member.sh"

OPERATION=""
OUTPUT_FILE=""
BEFORE_FILE=""
AFTER_FILE=""

log() { printf '%s\n' "$*"; }
fail() { printf 'PERSISTENT_DOGFOOD_OBSERVATION_ERROR %s\n' "$*" >&2; exit 1; }

usage() {
  cat <<'EOF'
Usage:
  persistent-dogfood-observation.sh capture --output FILE
  persistent-dogfood-observation.sh compare --before FILE --after FILE --output FILE

capture is read-only. It records only hashes, counts, booleans, and canonical
states for the persistent human subject, Mailpit volume/database, TLS identity,
and active Keycloak sessions. compare fails when either install changed them.

Required guard environment:
  WEAVE_DOGFOOD_DEPLOYMENT_SCOPE=persistent-dogfood
  TF_VAR_create_test_user=false
  TF_VAR_isolated_e2e_enabled=false
EOF
}

parse_args() {
  [[ $# -gt 0 ]] || { usage >&2; exit 2; }
  OPERATION="$1"
  shift
  case "${OPERATION}" in capture|compare) ;; -h|--help) usage; exit 0 ;; *) fail "unknown operation '${OPERATION}'" ;; esac
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --output) OUTPUT_FILE="${2:-}"; shift 2 ;;
      --before) BEFORE_FILE="${2:-}"; shift 2 ;;
      --after) AFTER_FILE="${2:-}"; shift 2 ;;
      -h|--help) usage; exit 0 ;;
      *) fail "unknown argument '$1'" ;;
    esac
  done
  [[ -n "${OUTPUT_FILE}" ]] || fail "--output is required"
  if [[ "${OPERATION}" == compare ]]; then
    [[ -n "${BEFORE_FILE}" && -n "${AFTER_FILE}" ]] || fail "compare requires --before and --after"
  fi
}

load_environment() {
  local requested_scope="${WEAVE_DOGFOOD_DEPLOYMENT_SCOPE:-}"
  local requested_create_test_user="${TF_VAR_create_test_user:-}"
  local requested_isolated="${TF_VAR_isolated_e2e_enabled:-}"
  local requested_namespace_set="${TF_VAR_isolated_e2e_namespace+x}"
  local requested_namespace="${TF_VAR_isolated_e2e_namespace:-}"
  local requested_memberships_set="${TF_VAR_isolated_e2e_context_memberships+x}"
  local requested_memberships="${TF_VAR_isolated_e2e_context_memberships:-}"
  local requested_caddy_ca="${TF_VAR_caddy_tls_ca_file:-}"
  local requested_caddy_cert="${TF_VAR_caddy_tls_cert_file:-}"
  local requested_caddy_key="${TF_VAR_caddy_tls_key_file:-}"
  if [[ -f "${BOOTSTRAP_ENV_FILE}" ]]; then
    # shellcheck disable=SC1090
    source "${BOOTSTRAP_ENV_FILE}"
  fi
  [[ -z "${requested_scope}" ]] || WEAVE_DOGFOOD_DEPLOYMENT_SCOPE="${requested_scope}"
  [[ -z "${requested_create_test_user}" ]] || TF_VAR_create_test_user="${requested_create_test_user}"
  [[ -z "${requested_isolated}" ]] || TF_VAR_isolated_e2e_enabled="${requested_isolated}"
  if [[ "${requested_namespace_set}" == x ]]; then
    TF_VAR_isolated_e2e_namespace="${requested_namespace}"
  else
    unset TF_VAR_isolated_e2e_namespace
  fi
  if [[ "${requested_memberships_set}" == x ]]; then
    TF_VAR_isolated_e2e_context_memberships="${requested_memberships}"
  else
    unset TF_VAR_isolated_e2e_context_memberships
  fi
  [[ -z "${requested_caddy_ca}" ]] || TF_VAR_caddy_tls_ca_file="${requested_caddy_ca}"
  [[ -z "${requested_caddy_cert}" ]] || TF_VAR_caddy_tls_cert_file="${requested_caddy_cert}"
  [[ -z "${requested_caddy_key}" ]] || TF_VAR_caddy_tls_key_file="${requested_caddy_key}"
}

assert_persistent_scope() {
  [[ "${WEAVE_DOGFOOD_DEPLOYMENT_SCOPE:-}" == "persistent-dogfood" ]] || fail "capture requires the explicit persistent-dogfood scope"
  [[ "${TF_VAR_create_test_user:-false}" == "false" ]] || fail "persistent dogfood must run with TF_VAR_create_test_user=false"
  [[ "${TF_VAR_isolated_e2e_enabled:-false}" == "false" ]] || fail "persistent dogfood cannot consume isolated E2E inputs"
  [[ -z "${TF_VAR_isolated_e2e_namespace:-}" ]] || fail "persistent dogfood cannot carry an isolated E2E namespace"
  [[ -z "${TF_VAR_isolated_e2e_context_memberships:-}" || "${TF_VAR_isolated_e2e_context_memberships}" == "[]" ]] ||
    fail "persistent dogfood cannot carry isolated E2E memberships"
}

sha256_stream() {
  shasum -a 256 | awk '{print $1}'
}

certificate_sha256() {
  local path="$1"
  [[ -f "${path}" ]] || fail "required TLS certificate is unavailable"
  openssl x509 -in "${path}" -outform DER | sha256_stream
}

keycloak_admin_url() {
  printf '%s' "${WEAVE_DOGFOOD_KEYCLOAK_ADMIN_URL:-http://127.0.0.1:${TF_VAR_keycloak_host_port:-48080}}"
}

admin_token() {
  curl --silent --show-error --fail \
    -X POST "$(keycloak_admin_url)/realms/master/protocol/openid-connect/token" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode 'client_id=admin-cli' \
    --data-urlencode "username=${TF_VAR_keycloak_admin_username:-admin}" \
    --data-urlencode "password=${TF_VAR_keycloak_admin_password:-}" \
    --data-urlencode 'grant_type=password' |
    jq -r '.access_token // empty'
}

active_sessions_summary() {
  local token="$1" subject="$2" realm="${TF_VAR_tenant_slug:-weave}" payload
  payload="$(curl --silent --fail \
    -H "Authorization: Bearer ${token}" \
    "$(keycloak_admin_url)/admin/realms/${realm}/users/${subject}/sessions")"
  jq -c '[.[] | select((.lastAccess // 0) >= 0) | (.id // "")] | sort | {count:length,setSha256:null,ids:.}' <<<"${payload}" |
    python3 -c 'import hashlib,json,sys
data=json.load(sys.stdin)
ids=data.pop("ids")
data["setSha256"]=hashlib.sha256("\n".join(ids).encode()).hexdigest()
print(json.dumps(data,separators=(",",":")))
'
}

mailpit_summary() {
  local volume_identity message_payload message_count database_size database_sha256
  volume_identity="$(docker volume inspect weave_mailpit_data --format '{{.Name}}|{{.CreatedAt}}|{{.Mountpoint}}')"
  message_payload="$(curl --silent --show-error --fail "http://127.0.0.1:${TF_VAR_mailpit_web_host_port:-8025}/api/v1/messages")"
  message_count="$(jq '(.total // .Total // (.messages // [] | length)) | tonumber' <<<"${message_payload}")"
  database_size="$(docker exec weave-mailpit sh -c 'if [ -f /data/mailpit.db ]; then wc -c </data/mailpit.db; else printf 0; fi' | tr -d '[:space:]')"
  [[ "${database_size}" =~ ^[0-9]+$ ]] || fail "Mailpit database size was not numeric"
  ((database_size > 0)) || fail "Mailpit database was empty or unavailable"
  # Hash the stream on the host so the container needs only `sh` and `cat`.
  # Database bytes are never written to evidence or command output.
  database_sha256="$(docker exec weave-mailpit sh -c 'exec cat /data/mailpit.db' | sha256_stream)"
  [[ "${database_sha256}" =~ ^[0-9a-f]{64}$ ]] || fail "Mailpit database SHA-256 was invalid"
  jq -cn \
    --arg identitySha256 "$(printf '%s' "${volume_identity}" | sha256_stream)" \
    --arg databaseSha256 "${database_sha256}" \
    --argjson messageCount "${message_count}" \
    --argjson databaseBytes "${database_size}" \
    '{volumeIdentitySha256:$identitySha256,messageCount:$messageCount,databaseBytes:$databaseBytes,databaseSha256:$databaseSha256}'
}

capture() {
  command -v curl >/dev/null || fail "curl is required"
  command -v docker >/dev/null || fail "docker is required"
  command -v jq >/dev/null || fail "jq is required"
  command -v openssl >/dev/null || fail "openssl is required"
  [[ -f "${DOGFOOD_MEMBER_SCRIPT}" ]] || fail "dogfood member helper is unavailable"
  : "${TF_VAR_caddy_tls_ca_file:?TF_VAR_caddy_tls_ca_file is required}"
  : "${TF_VAR_caddy_tls_cert_file:?TF_VAR_caddy_tls_cert_file is required}"
  : "${WEAVE_DOGFOOD_MEMBER_SUBJECT_FILE:=${ROOT_DIR}/.generated/dogfood-member.subject}"
  export WEAVE_DOGFOOD_MEMBER_SUBJECT_FILE
  [[ -s "${WEAVE_DOGFOOD_MEMBER_SUBJECT_FILE}" ]] || fail "persistent human subject file is unavailable"

  local member_evidence subject token sessions mailpit captured_at
  member_evidence="$(mktemp)"
  trap 'rm -f -- "${member_evidence:-}"' EXIT
  bash "${DOGFOOD_MEMBER_SCRIPT}" status --evidence-file "${member_evidence}" >/dev/null
  jq -e '.state == "active" and .supportSafe == true' "${member_evidence}" >/dev/null || fail "persistent human member is not active"
  subject="$(tr -d '\r\n' <"${WEAVE_DOGFOOD_MEMBER_SUBJECT_FILE}")"
  [[ -n "${subject}" ]] || fail "persistent human subject is empty"
  token="$(admin_token)"
  [[ -n "${token}" ]] || fail "Keycloak admin authentication failed"
  sessions="$(active_sessions_summary "${token}" "${subject}")"
  jq -e '.count > 0' <<<"${sessions}" >/dev/null || fail "persistent human member has no active Keycloak session"
  mailpit="$(mailpit_summary)"
  captured_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

  mkdir -p "$(dirname -- "${OUTPUT_FILE}")"
  jq -n \
    --arg capturedAt "${captured_at}" \
    --arg subjectSha256 "$(printf '%s' "${subject}" | sha256_stream)" \
    --arg caSha256 "$(certificate_sha256 "${TF_VAR_caddy_tls_ca_file}")" \
    --arg leafSha256 "$(certificate_sha256 "${TF_VAR_caddy_tls_cert_file}")" \
    --argjson sessions "${sessions}" \
    --argjson mailpit "${mailpit}" \
    '{
      schemaVersion:"weave.persistent-dogfood-observation.v1",
      capturedAt:$capturedAt,
      deploymentScope:"persistent-dogfood",
      staticTestUserEnabled:false,
      isolatedE2eEnabled:false,
      humanMember:{state:"active",subjectSha256:$subjectSha256},
      mailpit:$mailpit,
      tls:{caSha256:$caSha256,leafSha256:$leafSha256},
      activeSessions:$sessions,
      rawIdentityIncluded:false,
      credentialsIncluded:false,
      supportSafe:true
    }' >"${OUTPUT_FILE}"
  log "PERSISTENT_DOGFOOD_OBSERVATION state=captured supportSafe=true"
}

compare() {
  [[ -f "${BEFORE_FILE}" && -f "${AFTER_FILE}" ]] || fail "before/after observation is missing"
  jq -e '
    .schemaVersion == "weave.persistent-dogfood-observation.v1" and
    .supportSafe == true and
    .deploymentScope == "persistent-dogfood" and
    .staticTestUserEnabled == false and
    .isolatedE2eEnabled == false and
    (.mailpit.databaseBytes | type == "number" and . > 0) and
    (.mailpit.databaseSha256 | type == "string" and test("^[0-9a-f]{64}$"))
  ' "${BEFORE_FILE}" "${AFTER_FILE}" >/dev/null || fail "before/after observation schema or deployment scope is invalid"
  local compared_at result
  compared_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  mkdir -p "$(dirname -- "${OUTPUT_FILE}")"
  result="$(jq -n \
    --arg comparedAt "${compared_at}" \
    --slurpfile before "${BEFORE_FILE}" \
    --slurpfile after "${AFTER_FILE}" \
    'def same($path): ($before[0] | getpath($path)) == ($after[0] | getpath($path));
     [
       {gate:"human_subject",passed:same(["humanMember","subjectSha256"])},
       {gate:"human_active",passed:($before[0].humanMember.state == "active" and $after[0].humanMember.state == "active")},
       {gate:"mailpit_volume",passed:same(["mailpit","volumeIdentitySha256"])},
       {gate:"mailpit_message_count",passed:same(["mailpit","messageCount"])},
       {gate:"mailpit_database_size",passed:same(["mailpit","databaseBytes"])},
       {gate:"mailpit_database_hash",passed:same(["mailpit","databaseSha256"])},
       {gate:"tls_ca",passed:same(["tls","caSha256"])},
       {gate:"tls_leaf",passed:same(["tls","leafSha256"])},
       {gate:"active_session_count",passed:same(["activeSessions","count"])},
       {gate:"active_session_set",passed:same(["activeSessions","setSha256"])}
     ] as $gates
     | {
         schemaVersion:"weave.persistent-dogfood-comparison.v1",
         comparedAt:$comparedAt,
         status:(if all($gates[]; .passed) then "passed" else "failed" end),
         gates:$gates,
         twoNonDestructiveInstallsPreservedState:all($gates[]; .passed),
         supportSafe:true
       }')"
  printf '%s\n' "${result}" >"${OUTPUT_FILE}"
  jq -e '.status == "passed"' <<<"${result}" >/dev/null || fail "persistent dogfood state changed across the non-destructive installs"
  log "PERSISTENT_DOGFOOD_OBSERVATION state=preserved supportSafe=true"
}

main() {
  parse_args "$@"
  load_environment
  assert_persistent_scope
  case "${OPERATION}" in capture) capture ;; compare) compare ;; esac
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
