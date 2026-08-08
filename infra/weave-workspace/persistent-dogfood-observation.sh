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
  WEAVE_E2E_STACK_SCOPE=persistent
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
  local requested_scope="${WEAVE_E2E_STACK_SCOPE:-}"
  local requested_run_id="${WEAVE_E2E_RUN_ID:-}"
  local requested_run_namespace="${WEAVE_E2E_RUN_NAMESPACE:-}"
  local requested_caddy_ca="${WEAVE_CADDY_TLS_CA_FILE:-}"
  local requested_caddy_cert="${WEAVE_CADDY_TLS_CERT_FILE:-}"
  local requested_caddy_key="${WEAVE_CADDY_TLS_KEY_FILE:-}"
  if [[ -f "${BOOTSTRAP_ENV_FILE}" ]]; then
    # shellcheck disable=SC1090
    source "${BOOTSTRAP_ENV_FILE}"
  fi
  WEAVE_E2E_STACK_SCOPE="${requested_scope}"
  WEAVE_E2E_RUN_ID="${requested_run_id}"
  WEAVE_E2E_RUN_NAMESPACE="${requested_run_namespace}"
  unset WEAVE_CREATE_TEST_USER WEAVE_ISOLATED_E2E_ENABLED
  unset WEAVE_ISOLATED_E2E_NAMESPACE WEAVE_ISOLATED_E2E_CONTEXT_MEMBERSHIPS
  [[ -z "${requested_caddy_ca}" ]] || WEAVE_CADDY_TLS_CA_FILE="${requested_caddy_ca}"
  [[ -z "${requested_caddy_cert}" ]] || WEAVE_CADDY_TLS_CERT_FILE="${requested_caddy_cert}"
  [[ -z "${requested_caddy_key}" ]] || WEAVE_CADDY_TLS_KEY_FILE="${requested_caddy_key}"
}

assert_persistent_scope() {
  [[ "${WEAVE_E2E_STACK_SCOPE:-}" == "persistent" ]] || fail "capture requires WEAVE_E2E_STACK_SCOPE=persistent"
  [[ -z "${WEAVE_E2E_RUN_ID:-}" ]] || fail "persistent dogfood cannot carry an isolated E2E run identifier"
  [[ -z "${WEAVE_E2E_RUN_NAMESPACE:-}" ]] || fail "persistent dogfood cannot carry an isolated E2E namespace"
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
  printf '%s' "${WEAVE_DOGFOOD_KEYCLOAK_ADMIN_URL:-http://127.0.0.1:${WEAVE_KEYCLOAK_HOST_PORT:-48080}}"
}

admin_token() {
  curl --silent --show-error --fail \
    -X POST "$(keycloak_admin_url)/realms/master/protocol/openid-connect/token" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode 'client_id=admin-cli' \
    --data-urlencode "username=${WEAVE_KEYCLOAK_ADMIN_USERNAME:-admin}" \
    --data-urlencode "password=${WEAVE_KEYCLOAK_ADMIN_PASSWORD:-}" \
    --data-urlencode 'grant_type=password' |
    jq -r '.access_token // empty'
}

active_sessions_summary() {
  local token="$1" subject="$2" realm="${WEAVE_TENANT_SLUG:-weave}" payload
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
  local volume_name container_name volume_identity message_payload message_count database_size database_sha256
  volume_name="${WEAVE_MAILPIT_DATA_VOLUME:-weave_dogfood_mailpit_data}"
  container_name="${WEAVE_RESOURCE_PREFIX:-weave-dogfood}-mailpit"
  volume_identity="$(docker volume inspect "${volume_name}" --format '{{.Name}}|{{.CreatedAt}}|{{.Mountpoint}}')"
  message_payload="$(curl --silent --show-error --fail "http://127.0.0.1:${WEAVE_MAILPIT_WEB_HOST_PORT:-8025}/api/v1/messages")"
  message_count="$(jq '(.total // .Total // (.messages // [] | length)) | tonumber' <<<"${message_payload}")"
  database_size="$(docker exec "${container_name}" sh -c 'if [ -f /data/mailpit.db ]; then wc -c </data/mailpit.db; else printf 0; fi' | tr -d '[:space:]')"
  [[ "${database_size}" =~ ^[0-9]+$ ]] || fail "Mailpit database size was not numeric"
  ((database_size > 0)) || fail "Mailpit database was empty or unavailable"
  # Hash the stream on the host so the container needs only `sh` and `cat`.
  # Database bytes are never written to evidence or command output.
  database_sha256="$(docker exec "${container_name}" sh -c 'exec cat /data/mailpit.db' | sha256_stream)"
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
  : "${WEAVE_CADDY_TLS_CA_FILE:?WEAVE_CADDY_TLS_CA_FILE is required}"
  : "${WEAVE_CADDY_TLS_CERT_FILE:?WEAVE_CADDY_TLS_CERT_FILE is required}"
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
    --arg caSha256 "$(certificate_sha256 "${WEAVE_CADDY_TLS_CA_FILE}")" \
    --arg leafSha256 "$(certificate_sha256 "${WEAVE_CADDY_TLS_CERT_FILE}")" \
    --argjson sessions "${sessions}" \
    --argjson mailpit "${mailpit}" \
    '{
      schemaVersion:"weave.persistent-dogfood-observation.v2",
      capturedAt:$capturedAt,
      deploymentScope:"persistent-dogfood",
      e2eStackScope:"persistent",
      isolatedRunBound:false,
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
  local baseline_source="${WEAVE_PERSISTENT_BASELINE_SOURCE:-pre-deploy}"
  [[ "${baseline_source}" == pre-deploy || "${baseline_source}" == first-install ]] ||
    fail "baseline source must be pre-deploy or first-install"
  jq -e '
    .schemaVersion == "weave.persistent-dogfood-observation.v2" and
    .supportSafe == true and
    .deploymentScope == "persistent-dogfood" and
    .e2eStackScope == "persistent" and
    .isolatedRunBound == false and
    (.mailpit.databaseBytes | type == "number" and . > 0) and
    (.mailpit.databaseSha256 | type == "string" and test("^[0-9a-f]{64}$"))
  ' "${BEFORE_FILE}" "${AFTER_FILE}" >/dev/null || fail "before/after observation schema or deployment scope is invalid"
  local compared_at result
  compared_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  mkdir -p "$(dirname -- "${OUTPUT_FILE}")"
  result="$(jq -n \
    --arg comparedAt "${compared_at}" \
    --arg baselineSource "${baseline_source}" \
    --argjson preExistingRuntimeObserved "$([[ "${baseline_source}" == pre-deploy ]] && printf true || printf false)" \
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
         schemaVersion:"weave.persistent-dogfood-comparison.v2",
         comparedAt:$comparedAt,
         baselineSource:$baselineSource,
         preExistingRuntimeObserved:$preExistingRuntimeObserved,
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
