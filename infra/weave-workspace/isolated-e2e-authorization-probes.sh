#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly SCRIPT_DIR
# Reuse the identity helper's namespace derivation, marker validation, Keycloak
# Admin API wrappers, and backend runtime verification. Its main is source-safe.
# shellcheck source=infra/weave-workspace/isolated-e2e-identities.sh
source "${SCRIPT_DIR}/isolated-e2e-identities.sh"

AUTHORIZATION_EVIDENCE_PATH="${WEAVE_E2E_AUTHORIZATION_EVIDENCE_PATH:-}"
BACKEND_ORIGIN="${WEAVE_E2E_BACKEND_ORIGIN:-}"
SHORT_TOKEN_LIFESPAN_SECONDS="${WEAVE_E2E_SHORT_TOKEN_LIFESPAN_SECONDS:-2}"
TOKEN_EXPIRY_GRACE_SECONDS="${WEAVE_E2E_TOKEN_EXPIRY_GRACE_SECONDS:-65}"

PRIVATE_STATE_DIR=""
ADMIN_ACCESS_TOKEN=""
KEYCLOAK_API_BASE=""
AUTHOR_SUBJECT=""
COLLABORATOR_SUBJECT=""
CALENDAR_EDITOR_GROUP_ID=""
WEAVE_APP_CLIENT_ID=""
WORKSPACE_RESOURCE_AUDIENCE=""
GROUP_RESTORE_PENDING="false"
REALM_RESTORE_PENDING="false"
CLIENT_RESTORE_PENDING="false"
GROUP_RESTORED="false"
REALM_RESTORED="false"
CLIENT_RESTORED="false"

fail() {
  printf 'ISOLATED_E2E_AUTHORIZATION_ERROR %s\n' "$*" >&2
  exit 1
}

usage_authorization() {
  cat <<'EOF'
Usage: isolated-e2e-authorization-probes.sh --run-id ID [options]

Run destructive-but-restored authorization probes against a fully isolated
three-user E2E stack. Persistent dogfood is always rejected.

Options:
  --run-id ID                   Stable ID used by identity prepare/provision.
  --output-root PATH            Private run artifact root.
  --credentials-env PATH        Prepared private identity credential env.
  --startup-env PATH            Prepared isolated stack/OpenTofu env.
  --identity-manifest PATH      Provisioned support-safe identity evidence.
  --stack-bootstrap-env PATH    Private bootstrap env written by install.sh.
  --authorization-evidence PATH Support-safe authorization evidence output.

Environment:
  WEAVE_E2E_STACK_SCOPE=isolated                  Required.
  WEAVE_E2E_BACKEND_ORIGIN                       Backend origin; defaults to
                                                 the loopback backend port.
  WEAVE_E2E_SHORT_TOKEN_LIFESPAN_SECONDS         2..5; defaults to 2.
  WEAVE_E2E_TOKEN_EXPIRY_GRACE_SECONDS           60..90; defaults to 65 so the
                                                  probe exceeds Resource Server
                                                  clock-skew tolerance.
EOF
}

parse_authorization_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --run-id) RUN_ID="${2:-}"; shift 2 ;;
      --output-root) OUTPUT_ROOT="${2:-}"; shift 2 ;;
      --credentials-env) CREDENTIAL_ENV_PATH="${2:-}"; shift 2 ;;
      --startup-env) STARTUP_ENV_PATH="${2:-}"; shift 2 ;;
      --identity-manifest) IDENTITY_MANIFEST_PATH="${2:-}"; shift 2 ;;
      --stack-bootstrap-env) STACK_BOOTSTRAP_ENV="${2:-}"; shift 2 ;;
      --authorization-evidence) AUTHORIZATION_EVIDENCE_PATH="${2:-}"; shift 2 ;;
      -h|--help) usage_authorization; exit 0 ;;
      *) fail "unknown argument '$1'" ;;
    esac
  done
}

require_bounded_integer() {
  local name="$1" value="$2" minimum="$3" maximum="$4"
  [[ "${value}" =~ ^[0-9]+$ ]] || fail "${name} must be an integer"
  ((value >= minimum && value <= maximum)) ||
    fail "${name} must be between ${minimum} and ${maximum}"
}

initialize_paths() {
  derive_paths_and_names
  validate_paths
  AUTHORIZATION_EVIDENCE_PATH="${AUTHORIZATION_EVIDENCE_PATH:-${OUTPUT_ROOT}/${NAMESPACE}/authorization-evidence.json}"
  validate_private_path "${AUTHORIZATION_EVIDENCE_PATH}"
}

assert_provisioned_marker_evidence() {
  [[ -f "${IDENTITY_MANIFEST_PATH}" ]] || fail "provisioned identity manifest is missing"
  jq -e --arg namespaceSha256 "$(sha256 "${NAMESPACE}")" '
    .schemaVersion == "weave.isolated-e2e-identities.v1" and
    .namespaceSha256 == $namespaceSha256 and
    .contextAuthorization.status == "active_runtime_verified" and
    .providerBindings.keycloak == "provisioned" and
    .persistentHumanIdentityChanged == false and
    .supportSafe == true and
    (.actors | length == 3) and
    all(.actors[]; has("subjectSha256"))
  ' "${IDENTITY_MANIFEST_PATH}" >/dev/null ||
    fail "identity manifest is not the provisioned marker evidence for this namespace"
}

assert_subject_hash_bindings() {
  local author_hash collaborator_hash
  author_hash="$(sha256 "${AUTHOR_SUBJECT}")"
  collaborator_hash="$(sha256 "${COLLABORATOR_SUBJECT}")"
  jq -e \
    --arg authorHash "${author_hash}" \
    --arg collaboratorHash "${collaborator_hash}" '
      any(.actors[]; .role == "author" and .subjectSha256 == $authorHash) and
      any(.actors[]; .role == "collaborator" and .subjectSha256 == $collaboratorHash)
    ' "${IDENTITY_MANIFEST_PATH}" >/dev/null ||
    fail "authorization actors do not bind to the provisioned subject hashes"
}

resolve_marked_subject() {
  local username="$1" users subject user role
  users="$(resolve_user "${KEYCLOAK_API_BASE}" "${ADMIN_ACCESS_TOKEN}" "${username}")"
  subject="$(find_exact_id "${users}" username "${username}")"
  [[ -n "${subject}" ]] || fail "run-scoped identity is unavailable"
  user="$(resolve_user_by_id "${KEYCLOAK_API_BASE}" "${ADMIN_ACCESS_TOKEN}" "${subject}")"
  role="$(user_role_from_username "${username}")"
  user_marker_matches "${user}" "${username}" "${role}" ||
    fail "refusing to mutate an identity without this run's exact marker"
  printf '%s' "${subject}"
}

user_has_group() {
  local subject="$1" group_id="$2" groups
  groups="$(request GET "${KEYCLOAK_API_BASE}/users/${subject}/groups?briefRepresentation=false&max=1000" "${ADMIN_ACCESS_TOKEN}")"
  jq -e --arg id "${group_id}" 'any(.[]; .id == $id)' <<<"${groups}" >/dev/null
}

resolve_calendar_editor_group() {
  local groups group_id
  groups="$(request GET "${KEYCLOAK_API_BASE}/groups?search=$(encode weave-calendar-editors)&exact=true" "${ADMIN_ACCESS_TOKEN}")"
  group_id="$(find_exact_id "${groups}" name weave-calendar-editors)"
  [[ -n "${group_id}" ]] || fail "calendar editor capability group is unavailable"
  printf '%s' "${group_id}"
}

resolve_weave_app_client() {
  local clients client_id
  clients="$(request GET "${KEYCLOAK_API_BASE}/clients?clientId=weave-app" "${ADMIN_ACCESS_TOKEN}")"
  client_id="$(find_exact_id "${clients}" clientId weave-app)"
  [[ -n "${client_id}" ]] || fail "weave-app client is unavailable"
  printf '%s' "${client_id}"
}

resolve_workspace_resource_audience() {
  local scopes scope_id mappers audience
  scopes="$(request GET "${KEYCLOAK_API_BASE}/client-scopes" "${ADMIN_ACCESS_TOKEN}")"
  scope_id="$(find_exact_id "${scopes}" name weave:workspace)"
  [[ -n "${scope_id}" ]] || fail "weave:workspace client scope is unavailable"
  mappers="$(request GET "${KEYCLOAK_API_BASE}/client-scopes/${scope_id}/protocol-mappers/models" \
    "${ADMIN_ACCESS_TOKEN}")"
  audience="$(jq -r '
    [
      .[] |
      select(
        .name == "weave-backend-audience" and
        .protocol == "openid-connect" and
        .protocolMapper == "oidc-audience-mapper"
      ) |
      .config["included.client.audience"]
    ] |
    if length == 1 then .[0] else empty end
  ' <<<"${mappers}")"
  [[ -n "${audience}" && "${audience}" != "null" ]] ||
    fail "weave:workspace does not contain one exact backend audience mapper"
  printf '%s' "${audience}"
}

restore_group_now() {
  [[ "${GROUP_RESTORE_PENDING}" == "true" ]] || { GROUP_RESTORED="true"; return; }
  request PUT "${KEYCLOAK_API_BASE}/users/${COLLABORATOR_SUBJECT}/groups/${CALENDAR_EDITOR_GROUP_ID}" \
    "${ADMIN_ACCESS_TOKEN}" >/dev/null
  user_has_group "${COLLABORATOR_SUBJECT}" "${CALENDAR_EDITOR_GROUP_ID}" ||
    fail "calendar editor membership restoration did not verify"
  GROUP_RESTORE_PENDING="false"
  GROUP_RESTORED="true"
}

restore_realm_now() {
  [[ "${REALM_RESTORE_PENDING}" == "true" ]] || { REALM_RESTORED="true"; return; }
  request PUT "${KEYCLOAK_API_BASE}" "${ADMIN_ACCESS_TOKEN}" \
    "$(<"${PRIVATE_STATE_DIR}/realm-original.json")" >/dev/null
  request GET "${KEYCLOAK_API_BASE}" "${ADMIN_ACCESS_TOKEN}" >"${PRIVATE_STATE_DIR}/realm-restored.json"
  [[ "$(jq -c '.accessTokenLifespan' "${PRIVATE_STATE_DIR}/realm-restored.json")" == \
    "$(jq -c '.accessTokenLifespan' "${PRIVATE_STATE_DIR}/realm-original.json")" ]] ||
    fail "realm access-token lifespan restoration did not verify"
  REALM_RESTORE_PENDING="false"
  REALM_RESTORED="true"
}

restore_client_now() {
  [[ "${CLIENT_RESTORE_PENDING}" == "true" ]] || { CLIENT_RESTORED="true"; return; }
  request PUT "${KEYCLOAK_API_BASE}/clients/${WEAVE_APP_CLIENT_ID}" "${ADMIN_ACCESS_TOKEN}" \
    "$(<"${PRIVATE_STATE_DIR}/client-original.json")" >/dev/null
  request GET "${KEYCLOAK_API_BASE}/clients/${WEAVE_APP_CLIENT_ID}" "${ADMIN_ACCESS_TOKEN}" \
    >"${PRIVATE_STATE_DIR}/client-restored.json"
  [[ "$(jq -r '.directAccessGrantsEnabled' "${PRIVATE_STATE_DIR}/client-restored.json")" == \
    "$(jq -r '.directAccessGrantsEnabled' "${PRIVATE_STATE_DIR}/client-original.json")" ]] ||
    fail "weave-app direct-access setting restoration did not verify"
  CLIENT_RESTORE_PENDING="false"
  CLIENT_RESTORED="true"
}

restore_pending_state() {
  local failed=0 refreshed="" current=""
  set +e
  if [[ "${GROUP_RESTORE_PENDING}" == "true" ]]; then
    request PUT "${KEYCLOAK_API_BASE}/users/${COLLABORATOR_SUBJECT}/groups/${CALENDAR_EDITOR_GROUP_ID}" \
      "${ADMIN_ACCESS_TOKEN}" >/dev/null || failed=1
  fi
  if [[ "${REALM_RESTORE_PENDING}" == "true" && -f "${PRIVATE_STATE_DIR}/realm-original.json" ]]; then
    request PUT "${KEYCLOAK_API_BASE}" "${ADMIN_ACCESS_TOKEN}" \
      "$(<"${PRIVATE_STATE_DIR}/realm-original.json")" >/dev/null || failed=1
  fi
  if [[ "${CLIENT_RESTORE_PENDING}" == "true" && -f "${PRIVATE_STATE_DIR}/client-original.json" ]]; then
    request PUT "${KEYCLOAK_API_BASE}/clients/${WEAVE_APP_CLIENT_ID}" "${ADMIN_ACCESS_TOKEN}" \
      "$(<"${PRIVATE_STATE_DIR}/client-original.json")" >/dev/null || failed=1
  fi
  if ((failed != 0)); then
    refreshed="$(admin_token 2>/dev/null)"
    if [[ -n "${refreshed}" ]]; then
      ADMIN_ACCESS_TOKEN="${refreshed}"
      failed=0
      [[ "${GROUP_RESTORE_PENDING}" != "true" ]] ||
        request PUT "${KEYCLOAK_API_BASE}/users/${COLLABORATOR_SUBJECT}/groups/${CALENDAR_EDITOR_GROUP_ID}" \
          "${ADMIN_ACCESS_TOKEN}" >/dev/null || failed=1
      [[ "${REALM_RESTORE_PENDING}" != "true" || ! -f "${PRIVATE_STATE_DIR}/realm-original.json" ]] ||
        request PUT "${KEYCLOAK_API_BASE}" "${ADMIN_ACCESS_TOKEN}" \
          "$(<"${PRIVATE_STATE_DIR}/realm-original.json")" >/dev/null || failed=1
      [[ "${CLIENT_RESTORE_PENDING}" != "true" || ! -f "${PRIVATE_STATE_DIR}/client-original.json" ]] ||
        request PUT "${KEYCLOAK_API_BASE}/clients/${WEAVE_APP_CLIENT_ID}" "${ADMIN_ACCESS_TOKEN}" \
          "$(<"${PRIVATE_STATE_DIR}/client-original.json")" >/dev/null || failed=1
    fi
  fi
  if ((failed == 0)) && [[ "${GROUP_RESTORE_PENDING}" == "true" ]]; then
    user_has_group "${COLLABORATOR_SUBJECT}" "${CALENDAR_EDITOR_GROUP_ID}" || failed=1
  fi
  if ((failed == 0)) && [[ "${REALM_RESTORE_PENDING}" == "true" ]]; then
    current="$(request GET "${KEYCLOAK_API_BASE}" "${ADMIN_ACCESS_TOKEN}" 2>/dev/null)" || failed=1
    if [[ -z "${current}" ]] ||
      [[ "$(jq -c '.accessTokenLifespan' <<<"${current}")" != \
        "$(jq -c '.accessTokenLifespan' "${PRIVATE_STATE_DIR}/realm-original.json")" ]]; then
      failed=1
    fi
  fi
  if ((failed == 0)) && [[ "${CLIENT_RESTORE_PENDING}" == "true" ]]; then
    current="$(request GET "${KEYCLOAK_API_BASE}/clients/${WEAVE_APP_CLIENT_ID}" "${ADMIN_ACCESS_TOKEN}" 2>/dev/null)" || failed=1
    if [[ -z "${current}" ]] ||
      [[ "$(jq -r '.directAccessGrantsEnabled' <<<"${current}")" != \
        "$(jq -r '.directAccessGrantsEnabled' "${PRIVATE_STATE_DIR}/client-original.json")" ]]; then
      failed=1
    fi
  fi
  set -e
  return "${failed}"
}

on_exit() {
  local status=$?
  trap - EXIT
  if ! restore_pending_state; then
    printf 'ISOLATED_E2E_AUTHORIZATION_ERROR temporary Keycloak state restoration failed\n' >&2
    status=1
  fi
  [[ -z "${PRIVATE_STATE_DIR}" ]] || rm -rf "${PRIVATE_STATE_DIR}"
  exit "${status}"
}

enable_direct_grants_for_token_minting() {
  WEAVE_APP_CLIENT_ID="$(resolve_weave_app_client)"
  request GET "${KEYCLOAK_API_BASE}/clients/${WEAVE_APP_CLIENT_ID}" "${ADMIN_ACCESS_TOKEN}" \
    >"${PRIVATE_STATE_DIR}/client-original.json"
  jq -e '.clientId == "weave-app" and .publicClient == true and (.directAccessGrantsEnabled | type == "boolean")' \
    "${PRIVATE_STATE_DIR}/client-original.json" >/dev/null ||
    fail "weave-app client representation is not safe for isolated token minting"
  if [[ "$(jq -r '.directAccessGrantsEnabled' "${PRIVATE_STATE_DIR}/client-original.json")" != "true" ]]; then
    jq '.directAccessGrantsEnabled = true' "${PRIVATE_STATE_DIR}/client-original.json" \
      >"${PRIVATE_STATE_DIR}/client-direct-grants.json"
    CLIENT_RESTORE_PENDING="true"
    request PUT "${KEYCLOAK_API_BASE}/clients/${WEAVE_APP_CLIENT_ID}" "${ADMIN_ACCESS_TOKEN}" \
      "$(<"${PRIVATE_STATE_DIR}/client-direct-grants.json")" >/dev/null
    request GET "${KEYCLOAK_API_BASE}/clients/${WEAVE_APP_CLIENT_ID}" "${ADMIN_ACCESS_TOKEN}" \
      >"${PRIVATE_STATE_DIR}/client-active.json"
    jq -e '.directAccessGrantsEnabled == true' "${PRIVATE_STATE_DIR}/client-active.json" >/dev/null ||
      fail "isolated token-minting client setting did not activate"
  fi
}

mint_user_token() {
  local username="$1" password="$2" response_file status token
  response_file="$(mktemp "${PRIVATE_STATE_DIR}/token-response.XXXXXX")"
  status="$(curl --silent --show-error --output "${response_file}" --write-out '%{http_code}' \
    --connect-timeout 5 --max-time 15 \
    -X POST "$(keycloak_admin_url)/realms/$(encode "${REALM}")/protocol/openid-connect/token" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode 'client_id=weave-app' \
    --data-urlencode "username=${username}" \
    --data-urlencode "password=${password}" \
    --data-urlencode 'grant_type=password' \
    --data-urlencode 'scope=openid profile email weave:workspace')"
  [[ "${status}" == "200" ]] || fail "isolated Keycloak token mint failed with status ${status}"
  token="$(jq -r '.access_token // empty' "${response_file}")"
  rm -f "${response_file}"
  [[ -n "${token}" ]] || fail "isolated Keycloak token response did not contain an access token"
  printf '%s' "${token}"
}

jwt_payload() {
  python3 -c '
import base64, json, sys
token = sys.stdin.read().strip()
parts = token.split(".")
if len(parts) != 3:
    raise SystemExit(1)
payload = parts[1] + "=" * (-len(parts[1]) % 4)
value = json.loads(base64.urlsafe_b64decode(payload.encode("ascii")))
json.dump(value, sys.stdout, separators=(",", ":"), sort_keys=True)
' <<<"$1"
}

validate_workspace_token() {
  local token="$1" expected_username="$2" payload
  [[ -n "${WORKSPACE_RESOURCE_AUDIENCE}" ]] || fail "workspace resource audience is unresolved"
  payload="$(jwt_payload "${token}")" || fail "minted token is not a JWT"
  jq -e \
    --arg username "${expected_username}" \
    --arg audience "${WORKSPACE_RESOURCE_AUDIENCE}" '
    .preferred_username == $username and
    ((.aud | if type == "array" then . else [.] end) | index($audience) != null) and
    ((.scope // "") | split(" ") | index("weave:workspace") != null) and
    (.exp | type == "number") and (.iat | type == "number") and (.exp > .iat)
  ' <<<"${payload}" >/dev/null || fail "minted token does not satisfy the real workspace JWT contract"
}

token_has_group() {
  local token="$1" group="$2"
  jwt_payload "${token}" | jq -e --arg group "${group}" '(.groups // []) | index($group) != null' >/dev/null
}

token_lifetime_seconds() {
  jwt_payload "$1" | jq -r '.exp - .iat'
}

token_expiry_epoch() {
  jwt_payload "$1" | jq -r '.exp'
}

set_short_realm_lifespan() {
  request GET "${KEYCLOAK_API_BASE}" "${ADMIN_ACCESS_TOKEN}" |
    jq '{accessTokenLifespan:.accessTokenLifespan}' >"${PRIVATE_STATE_DIR}/realm-original.json"
  jq -e '(.accessTokenLifespan | type == "number") and (.accessTokenLifespan >= 1)' \
    "${PRIVATE_STATE_DIR}/realm-original.json" >/dev/null ||
    fail "isolated realm did not expose a restorable access-token lifespan"
  jq --argjson seconds "${SHORT_TOKEN_LIFESPAN_SECONDS}" '.accessTokenLifespan = $seconds' \
    "${PRIVATE_STATE_DIR}/realm-original.json" >"${PRIVATE_STATE_DIR}/realm-short.json"
  REALM_RESTORE_PENDING="true"
  request PUT "${KEYCLOAK_API_BASE}" "${ADMIN_ACCESS_TOKEN}" \
    "$(<"${PRIVATE_STATE_DIR}/realm-short.json")" >/dev/null
  request GET "${KEYCLOAK_API_BASE}" "${ADMIN_ACCESS_TOKEN}" >"${PRIVATE_STATE_DIR}/realm-active.json"
  [[ "$(jq -r '.accessTokenLifespan' "${PRIVATE_STATE_DIR}/realm-active.json")" == \
    "${SHORT_TOKEN_LIFESPAN_SECONDS}" ]] || fail "bounded realm access-token lifespan did not activate"
}

probe_status() {
  local method="$1" path="$2" token="$3"
  local -a args=(
    --silent --show-error --output /dev/null --write-out '%{http_code}'
    --connect-timeout 5 --max-time 15
    -X "${method}" -H "Authorization: Bearer ${token}"
  )
  if [[ "${method}" == "PROPFIND" ]]; then
    args+=(-H 'Depth: 0')
  fi
  if [[ -n "${WEAVE_TLS_CA_FILE:-}" && -f "${WEAVE_TLS_CA_FILE}" ]]; then
    args+=(--cacert "${WEAVE_TLS_CA_FILE}")
  fi
  curl "${args[@]}" "${BACKEND_ORIGIN}${path}"
}

calendar_missing_capability_status() {
  local token="$1" uid="${NAMESPACE}-missing-capability" status
  local body_file="${PRIVATE_STATE_DIR}/missing-capability-body" headers_file="${PRIVATE_STATE_DIR}/missing-capability-headers"
  cat >"${PRIVATE_STATE_DIR}/missing-capability.ics" <<ICS
BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//Weave//Isolated E2E//EN
BEGIN:VEVENT
UID:${uid}
DTSTAMP:20260712T120000Z
DTSTART:20260713T120000Z
DTEND:20260713T123000Z
SUMMARY:Isolated authorization fixture
END:VEVENT
END:VCALENDAR
ICS
  local -a args=(
    --silent --show-error --output "${body_file}" --dump-header "${headers_file}" --write-out '%{http_code}'
    --connect-timeout 5 --max-time 15
    -X PUT -H "Authorization: Bearer ${token}"
    -H 'Content-Type: text/calendar; charset=UTF-8' -H 'If-None-Match: *'
    --data-binary "@${PRIVATE_STATE_DIR}/missing-capability.ics"
  )
  if [[ -n "${WEAVE_TLS_CA_FILE:-}" && -f "${WEAVE_TLS_CA_FILE}" ]]; then
    args+=(--cacert "${WEAVE_TLS_CA_FILE}")
  fi
  status="$(curl "${args[@]}" "${BACKEND_ORIGIN}/caldav/workspace/${uid}.ics")"
  if [[ "${status}" =~ ^20[014]$ ]]; then
    probe_status DELETE "/caldav/workspace/${uid}.ics" "$2" >/dev/null || true
  fi
  [[ "${status}" == "403" ]] || fail "missing Calendar capability write returned status ${status}, expected 403"
  grep -Eiq '^X-Weave-Error-Code:[[:space:]]*capability-policy-blocked[[:space:]]*$' "${headers_file}" ||
    fail "missing capability response did not use the support-safe capability error code"
  if grep -Eiq 'authorization:|bearer[[:space:]]|https?://|nextcloud|secretref://' "${body_file}" ||
    grep -Fq "${token}" "${body_file}" || grep -Fq "$2" "${body_file}" ||
    grep -Fq "${AUTHOR_PASSWORD}" "${body_file}" || grep -Fq "${COLLABORATOR_PASSWORD}" "${body_file}"; then
    fail "missing capability response was not support-safe"
  fi
  printf '%s' "${status}"
}

write_evidence() {
  local missing_status="$1" expired_chat="$2" expired_files="$3" expired_calendar="$4" logout_status="$5" revoked_status="$6"
  local completed_at
  completed_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  mkdir -p "$(dirname -- "${AUTHORIZATION_EVIDENCE_PATH}")"
  jq -n \
    --arg completedAtUtc "${completed_at}" \
    --arg namespaceSha256 "$(sha256 "${NAMESPACE}")" \
    --arg missingActorSha256 "$(sha256 "${COLLABORATOR_SUBJECT}")" \
    --arg revokedActorSha256 "$(sha256 "${AUTHOR_SUBJECT}")" \
    --argjson missingStatus "${missing_status}" \
    --argjson expiredChat "${expired_chat}" \
    --argjson expiredFiles "${expired_files}" \
    --argjson expiredCalendar "${expired_calendar}" \
    --argjson logoutStatus "${logout_status}" \
    --argjson revokedStatus "${revoked_status}" \
    '{
      schemaVersion:"weave.isolated-e2e-authorization.v1",
      completedAtUtc:$completedAtUtc,
      namespaceSha256:$namespaceSha256,
      isolatedRuntimeVerified:true,
      markerOwnedIdentitiesVerified:true,
      missingCapability:{
        actorSha256:$missingActorSha256,
        calendarWriteStatus:$missingStatus,
        groupRemovedBeforeMint:true,
        freshTokenClaimExcludedGroup:true,
        supportSafeResponse:true,
        groupRestored:true
      },
      expiredToken:{
        boundedLifetimeVerified:true,
        realmSettingRestoredBeforeWait:true,
        chatStatus:$expiredChat,
        filesStatus:$expiredFiles,
        calendarStatus:$expiredCalendar
      },
      revokedSession:{
        actorSha256:$revokedActorSha256,
        matrixLogoutStatus:$logoutStatus,
        tokenUnexpiredAtLogout:true,
        chatReuseStatus:$revokedStatus
      },
      restoration:{
        calendarEditorMembership:true,
        realmAccessTokenLifespan:true,
        weaveAppDirectAccessGrants:true
      },
      persistentHumanChanged:false,
      rawIdentityIncluded:false,
      rawTokenIncluded:false,
      rawProviderPayloadIncluded:false,
      supportSafe:true
    }' >"${AUTHORIZATION_EVIDENCE_PATH}"
}

run_authorization_probes() {
  command -v curl >/dev/null || fail "curl is required"
  command -v docker >/dev/null || fail "docker is required"
  command -v jq >/dev/null || fail "jq is required"
  command -v python3 >/dev/null || fail "python3 is required"
  require_bounded_integer WEAVE_E2E_SHORT_TOKEN_LIFESPAN_SECONDS "${SHORT_TOKEN_LIFESPAN_SECONDS}" 2 5
  require_bounded_integer WEAVE_E2E_TOKEN_EXPIRY_GRACE_SECONDS "${TOKEN_EXPIRY_GRACE_SECONDS}" 60 90

  load_runtime_environment
  assert_isolated_runtime
  verify_backend_rebac_runtime
  assert_provisioned_marker_evidence
  [[ -n "${TF_VAR_keycloak_admin_password:-}" ]] || fail "isolated Keycloak admin credential is missing"

  BACKEND_ORIGIN="${BACKEND_ORIGIN:-http://127.0.0.1:${TF_VAR_backend_host_port:-48081}}"
  BACKEND_ORIGIN="${BACKEND_ORIGIN%/}"
  [[ "${BACKEND_ORIGIN}" =~ ^https?:// ]] || fail "backend origin must be HTTP(S)"

  PRIVATE_STATE_DIR="$(mktemp -d "${TMPDIR:-/tmp}/weave-isolated-auth.XXXXXX")"
  chmod 700 "${PRIVATE_STATE_DIR}"
  umask 077
  trap on_exit EXIT

  ADMIN_ACCESS_TOKEN="$(admin_token)"
  [[ -n "${ADMIN_ACCESS_TOKEN}" ]] || fail "isolated Keycloak admin authentication failed"
  KEYCLOAK_API_BASE="$(api_base)"

  AUTHOR_SUBJECT="$(resolve_marked_subject "${AUTHOR_USERNAME}")"
  COLLABORATOR_SUBJECT="$(resolve_marked_subject "${COLLABORATOR_USERNAME}")"
  resolve_marked_subject "${OUTSIDER_USERNAME}" >/dev/null
  assert_subject_hash_bindings
  CALENDAR_EDITOR_GROUP_ID="$(resolve_calendar_editor_group)"
  user_has_group "${COLLABORATOR_SUBJECT}" "${CALENDAR_EDITOR_GROUP_ID}" ||
    fail "collaborator must start in the calendar editor group"

  enable_direct_grants_for_token_minting
  WORKSPACE_RESOURCE_AUDIENCE="$(resolve_workspace_resource_audience)"

  local revoked_token missing_token expired_token
  # Spring Resource Server intentionally tolerates clock skew after exp. Wait
  # past both the bounded token lifetime and that tolerance before asserting
  # rejection, while retaining a hard upper bound through input validation.
  local expiry_wait=$((SHORT_TOKEN_LIFESPAN_SECONDS + TOKEN_EXPIRY_GRACE_SECONDS))
  revoked_token="$(mint_user_token "${AUTHOR_USERNAME}" "${AUTHOR_PASSWORD}")"
  validate_workspace_token "${revoked_token}" "${AUTHOR_USERNAME}"
  (( $(token_expiry_epoch "${revoked_token}") - $(date +%s) > expiry_wait + 5 )) ||
    fail "revocation token lifetime is too short to distinguish revocation from expiry"

  GROUP_RESTORE_PENDING="true"
  request DELETE "${KEYCLOAK_API_BASE}/users/${COLLABORATOR_SUBJECT}/groups/${CALENDAR_EDITOR_GROUP_ID}" \
    "${ADMIN_ACCESS_TOKEN}" >/dev/null
  ! user_has_group "${COLLABORATOR_SUBJECT}" "${CALENDAR_EDITOR_GROUP_ID}" ||
    fail "calendar editor membership removal did not verify"
  missing_token="$(mint_user_token "${COLLABORATOR_USERNAME}" "${COLLABORATOR_PASSWORD}")"
  validate_workspace_token "${missing_token}" "${COLLABORATOR_USERNAME}"
  ! token_has_group "${missing_token}" weave-calendar-editors ||
    fail "fresh missing-capability token still contains the removed group"
  restore_group_now

  set_short_realm_lifespan
  expired_token="$(mint_user_token "${AUTHOR_USERNAME}" "${AUTHOR_PASSWORD}")"
  validate_workspace_token "${expired_token}" "${AUTHOR_USERNAME}"
  local short_lifetime
  short_lifetime="$(token_lifetime_seconds "${expired_token}")"
  [[ "${short_lifetime}" =~ ^[0-9]+$ ]] || fail "bounded token lifetime was not numeric"
  ((short_lifetime >= 1 && short_lifetime <= SHORT_TOKEN_LIFESPAN_SECONDS + 1)) ||
    fail "minted token did not use the bounded isolated realm lifetime"
  restore_realm_now
  restore_client_now

  local missing_status expired_chat expired_files expired_calendar logout_status revoked_status
  missing_status="$(calendar_missing_capability_status "${missing_token}" "${revoked_token}")"

  sleep "${expiry_wait}"
  expired_chat="$(probe_status GET '/_matrix/client/v3/account/whoami' "${expired_token}")"
  expired_files="$(probe_status PROPFIND '/dav/files/' "${expired_token}")"
  expired_calendar="$(probe_status PROPFIND '/caldav/' "${expired_token}")"
  [[ "${expired_chat}:${expired_files}:${expired_calendar}" == "401:401:401" ]] ||
    fail "expired token did not fail closed across Chat, Files, and Calendar"

  (( $(token_expiry_epoch "${revoked_token}") > $(date +%s) )) ||
    fail "revocation token expired before the logout probe"
  logout_status="$(probe_status POST '/_matrix/client/v3/logout' "${revoked_token}")"
  [[ "${logout_status}" == "200" ]] || fail "Matrix logout returned status ${logout_status}, expected 200"
  revoked_status="$(probe_status GET '/_matrix/client/v3/account/whoami' "${revoked_token}")"
  [[ "${revoked_status}" == "401" ]] || fail "revoked Matrix token returned status ${revoked_status}, expected 401"

  [[ "${GROUP_RESTORED}:${REALM_RESTORED}:${CLIENT_RESTORED}" == "true:true:true" ]] ||
    fail "temporary authorization fixture state was not fully restored"
  write_evidence "${missing_status}" "${expired_chat}" "${expired_files}" "${expired_calendar}" \
    "${logout_status}" "${revoked_status}"
  printf 'MULTI_USER_AUTHORIZATION_FIXTURES_RESULT status=passed missingCapabilityStatus=403 expiredChatStatus=401 expiredFilesStatus=401 expiredCalendarStatus=401 matrixLogoutStatus=200 revokedChatStatus=401 persistentHumanChanged=false supportSafe=true\n'
}

main_authorization() {
  parse_authorization_args "$@"
  initialize_paths
  run_authorization_probes
}

main_authorization "$@"
