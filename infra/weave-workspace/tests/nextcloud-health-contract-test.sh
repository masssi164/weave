#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
TEST_ROOT="${ROOT_DIR}"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

fail() { printf '%s\n' "$*" >&2; exit 1; }

file_mode() {
  local path="$1"
  if stat -c '%a' "${path}" >/dev/null 2>&1; then
    stat -c '%a' "${path}"
  else
    stat -f '%Lp' "${path}"
  fi
}

nextcloud_module="${ROOT_DIR}/01-infrastructure/modules/nextcloud/main.tf"
variables="${ROOT_DIR}/01-infrastructure/variables.tf"
install_script="${ROOT_DIR}/install.sh"
caddy_template="${ROOT_DIR}/01-infrastructure/templates/Caddyfile.tpl"
release_env="${ROOT_DIR}/release.env.example"

if grep -Eq 'TRUSTED_PROXIES=|nextcloud_trusted_proxies' "${nextcloud_module}" "${variables}" "${install_script}" "${release_env}"; then
  fail "Nextcloud proxy trust must not use a static network-wide input"
fi
grep -Fq 'FORWARDEDFORHEADERS=HTTP_X_FORWARDED_FOR' "${nextcloud_module}"
grep -Fq 'header_up X-Forwarded-For {http.request.remote.host}' "${caddy_template}"
grep -Fq 'header_up X-Forwarded-Host {host}' "${caddy_template}"
grep -Fq 'header_up X-Forwarded-Proto {scheme}' "${caddy_template}"
grep -Fq 'configure_nextcloud_reverse_proxy' "${install_script}"
grep -Fq 'single-bounded-authenticated-attempt-per-protocol' "${install_script}"
grep -Fq 'no retry was attempted' "${install_script}"

mkdir -p "${TMP_DIR}/workspace"
cp "${install_script}" "${TMP_DIR}/workspace/install.sh"
export WEAVE_NEXTCLOUD_PROVISION_EVIDENCE_FILE="${TMP_DIR}/nextcloud-evidence.json"
# shellcheck source=/dev/null
source "${TMP_DIR}/workspace/install.sh"

occ_status_json=''
occ() {
  [[ "${1:-}" == status && "${2:-}" == --output=json ]] || return 1
  printf '%s\n' "${occ_status_json}"
}

occ_status_json='{"installed":true,"version":"fixture"}'
nextcloud_is_installed || fail "compact Nextcloud status JSON was not recognized"
occ_status_json=$'{\n  "installed": true,\n  "version": "fixture"\n}'
nextcloud_is_installed || fail "pretty-printed Nextcloud status JSON was not recognized"
occ_status_json='{"installed":false,"version":"fixture"}'
if nextcloud_is_installed; then
  fail "an uninstalled Nextcloud status was accepted"
fi

install_race_state="${TMP_DIR}/nextcloud-install-race-state"
printf '0\n' >"${install_race_state}"
occ() {
  case "${1:-}" in
    status)
      local status_calls
      status_calls="$(cat "${install_race_state}")"
      status_calls="$((status_calls + 1))"
      printf '%s\n' "${status_calls}" >"${install_race_state}"
      if ((status_calls == 1)); then
        printf '%s\n' '{"installed": false}'
      else
        printf '%s\n' '{"installed": true}'
      fi
      ;;
    maintenance:install)
      return 1
      ;;
    *)
      return 1
      ;;
  esac
}
terraform_output_raw() { printf '%s\n' 'fixture-nextcloud'; }
export TF_VAR_nextcloud_db_username='fixture-db-user'
export TF_VAR_nextcloud_db_password='fixture-db-password'
export TF_VAR_nextcloud_admin_username='fixture-admin'
export TF_VAR_nextcloud_admin_password='fixture-admin-password'
ensure_nextcloud_installed >/dev/null
[[ "$(cat "${install_race_state}")" == 2 ]] || fail "Nextcloud install convergence was not rechecked after a concurrent install"

oidc_command_state="${TMP_DIR}/nextcloud-oidc-command-state"
printf '0\n' >"${oidc_command_state}"
nextcloud_occ_with_timeout() {
  local timeout_seconds="$1"
  shift
  [[ "${timeout_seconds}" == 1 ]] || return 1
  [[ "${1:-}" == list && "${2:-}" == --raw ]] || return 1
  local list_calls
  list_calls="$(cat "${oidc_command_state}")"
  list_calls="$((list_calls + 1))"
  printf '%s\n' "${list_calls}" >"${oidc_command_state}"
  printf '%s\n' 'status' 'app:enable'
  if ((list_calls > 1)); then
    printf '%s\n' 'user_oidc:provider                      Create or update an OIDC provider'
  fi
}
wait_for_nextcloud_occ_command user_oidc:provider 2 0 1
[[ "$(cat "${oidc_command_state}")" == 2 ]] || fail "Nextcloud OIDC command readiness was not retried"

timeout_status=0
run_command_with_timeout 0.01 python3 -c 'import time; time.sleep(1)' || timeout_status=$?
[[ "${timeout_status}" == 124 ]] || fail "command timeout did not fail with the bounded-timeout status"

export TF_VAR_docker_network_name="weave-e2e-fixture_network"
export TF_VAR_nextcloud_backend_actor_username="fixture-actor"
export TF_VAR_nextcloud_backend_actor_token="fixture-token"
export TF_VAR_tenant_domain="weave.test"
export TF_VAR_nextcloud_subdomain="files"
export TF_VAR_public_scheme="https"
export TF_VAR_proxy_host_port="443"
export TF_VAR_caddy_tls_ca_file="${TMP_DIR}/missing-ca.pem"

occ_state="${TMP_DIR}/occ-state"
mkdir -p "${occ_state}"
occ() {
  local operation="${1:-}" key="${2:-}" index="${3:-}" value="${4:-}"
  case "${operation}" in
    config:system:set)
      if [[ "${index}" == --value=* ]]; then value="${index}"; index="value"; fi
      value="${value#--value=}"
      printf '%s' "${value}" >"${occ_state}/${key}.${index}"
      ;;
    config:system:get)
      [[ -f "${occ_state}/${key}.${index}" ]] || return 1
      cat "${occ_state}/${key}.${index}"
      ;;
    config:system:delete)
      rm -f "${occ_state}/${key}."*
      ;;
    *) return 1 ;;
  esac
}

docker() {
  [[ "${1:-}" == inspect ]] || return 1
  local container="${4:-}"
  case "${container}" in
    weave-proxy) printf '{"weave-e2e-fixture_network":{"IPAddress":"172.31.20.2","GlobalIPv6Address":""}}\n' ;;
    weave-nextcloud) printf '{"weave-e2e-fixture_network":{"IPAddress":"172.31.20.3","GlobalIPv6Address":""}}\n' ;;
    *) return 1 ;;
  esac
}

configure_nextcloud_reverse_proxy >/dev/null
[[ "$(cat "${occ_state}/trusted_proxies.0")" == "172.31.20.2" ]] || fail "exact Caddy address was not configured"
[[ ! -e "${occ_state}/trusted_proxies.1" ]] || fail "unexpected extra trusted proxy was configured"
[[ "$(cat "${occ_state}/forwarded_for_headers.0")" == "HTTP_X_FORWARDED_FOR" ]] || fail "forwarded header was not pinned"
[[ "$(cat "${occ_state}/overwritecondaddr.value")" == '^(?:172\.31\.20\.2)$' ]] ||
  fail "overwrite condition was not scoped to the exact Caddy address"

dav_calls="${TMP_DIR}/dav-calls"
: >"${dav_calls}"
# shellcheck disable=SC2329
curl_nextcloud_actor_dav_status() {
  local method="$1" url="$2" headers="$3"
  printf '%s %s\n' "${method}" "${url}" >>"${dav_calls}"
  : >"${headers}"
  printf '207'
}
verify_nextcloud_dav_post_provision >/dev/null
[[ "$(wc -l <"${dav_calls}" | tr -d ' ')" == 2 ]] || fail "post-provision verification must make exactly one WebDAV and one CalDAV request"
jq -e '.status == "passed" and .webdav.attempts == 1 and .caldav.attempts == 1 and .readinessPollingPerformedProviderAuthentication == false' \
  "${WEAVE_NEXTCLOUD_PROVISION_EVIDENCE_FILE}" >/dev/null

: >"${dav_calls}"
curl_nextcloud_actor_dav_status() {
  local method="$1" url="$2" headers="$3"
  printf '%s %s\n' "${method}" "${url}" >>"${dav_calls}"
  printf 'HTTP/1.1 429 Too Many Requests\r\nRetry-After: 120\r\n\r\n' >"${headers}"
  printf '429'
}
if (verify_nextcloud_dav_post_provision >/dev/null 2>&1); then
  fail "throttled DAV verification should fail closed"
fi
[[ "$(wc -l <"${dav_calls}" | tr -d ' ')" == 1 ]] || fail "throttled DAV verification must not retry or continue to CalDAV"
jq -e '.status == "failed" and .retryAfterObserved == true and .webdav.attempts == 1 and .caldav.attempts == 0' \
  "${WEAVE_NEXTCLOUD_PROVISION_EVIDENCE_FILE}" >/dev/null

security_log="${TMP_DIR}/nextcloud.log"
cat >"${security_log}" <<'JSON'
{"time":"2026-07-12T10:00:00Z","remoteAddr":"172.31.20.4","user":"fixture-service-actor","method":"PROPFIND","url":"/remote.php/dav/files/fixture-service-actor/","message":"Login failed: invalid password for fixture-service-actor"}
{"time":"2026-07-12T10:00:30Z","remoteAddr":"192.0.2.43","user":"--","method":"PROPFIND","url":"/remote.php/dav/files/fixture-service-actor/shared","message":"Authentication failed for fixture-service-actor"}
{"time":"2026-07-12T10:01:00Z","remoteAddr":"192.0.2.44","method":"REPORT","url":"/remote.php/dav/calendars/private-user/workspace/","message":"Bruteforce attempt throttled","status":"429"}
{"time":"2026-07-12T10:02:00Z","remoteAddr":"192.0.2.45","method":"GET","url":"/private/raw/path","message":"ordinary request containing private content"}
JSON
security_evidence="${TMP_DIR}/security-evidence.json"
WEAVE_AUDIT_HASH_SALT=fixture-salt \
WEAVE_AUDIT_PROXY_IP=172.31.20.2 \
WEAVE_AUDIT_BACKEND_IP=172.31.20.4 \
WEAVE_AUDIT_NEXTCLOUD_IP=172.31.20.3 \
TF_VAR_nextcloud_backend_actor_username=fixture-service-actor \
  bash "${TEST_ROOT}/nextcloud-auth-security-audit.sh" --log-file "${security_log}" --output "${security_evidence}"
jq -e '
  .schemaVersion == "weave-nextcloud-auth-security-audit-v1" and
  .eventsObserved == 3 and
  any(.sources[]; .sourceClass == "backend") and
  any(.sources[]; .sourceClass == "external_or_unclassified") and
  any(.requestClassifications[];
    .methodClass == "PROPFIND" and
    .routeClass == "files_webdav" and
    .invalidAuthenticationEvents == 2 and
    .throttleEvents == 0 and
    .backendActorFailureEvents == 2) and
  any(.requestClassifications[];
    .methodClass == "REPORT" and
    .routeClass == "calendar_caldav" and
    .invalidAuthenticationEvents == 1 and
    .throttleEvents == 1 and
    .backendActorFailureEvents == 0) and
  .backendActorAttribution.configured == true and
  .backendActorAttribution.failureObserved == true and
  .backendActorAttribution.failureEvents == 2 and
  .bruteForceProtectionChanged == false and
  .countersReset == false
' \
  "${security_evidence}" >/dev/null
if grep -Eq '172\.31\.20\.4|192\.0\.2\.4[34]|private-user|fixture-service-actor|invalid password|ordinary request|remote\.php|/dav/' "${security_evidence}"; then
  fail "security audit leaked a raw address, actor, URL, message, or provider payload"
fi

unconfigured_actor_evidence="${TMP_DIR}/security-evidence-unconfigured-actor.json"
WEAVE_AUDIT_HASH_SALT=fixture-salt \
WEAVE_AUDIT_PROXY_IP=172.31.20.2 \
WEAVE_AUDIT_BACKEND_IP=172.31.20.4 \
WEAVE_AUDIT_NEXTCLOUD_IP=172.31.20.3 \
TF_VAR_nextcloud_backend_actor_username='' \
WEAVE_NEXTCLOUD_BACKEND_ACTOR_USERNAME='' \
  bash "${TEST_ROOT}/nextcloud-auth-security-audit.sh" --log-file "${security_log}" --output "${unconfigured_actor_evidence}"
jq -e '
  .backendActorAttribution.configured == false and
  .backendActorAttribution.failureObserved == false and
  .backendActorAttribution.failureEvents == 0 and
  all(.requestClassifications[]; .backendActorFailureEvents == 0)
' "${unconfigured_actor_evidence}" >/dev/null

audit_bin="${TMP_DIR}/audit-bin"
mkdir -p "${audit_bin}"
cat >"${audit_bin}/docker" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
if [[ "${1:-}" == exec && "${2:-}" == weave-nextcloud ]]; then
  cat "${MOCK_SECURITY_LOG}"
  exit 0
fi
if [[ "${1:-}" != inspect ]]; then
  exit 1
fi
template="${3:-}"
container="${4:-}"
if [[ "${template}" == '{{json .Config.Env}}' && "${container}" == weave-backend ]]; then
  printf '["WEAVE_NEXTCLOUD_FILES_ACTOR_USERNAME=fixture-service-actor","UNRELATED_SECRET=must-not-appear"]\n'
  exit 0
fi
case "${container}" in
  weave-proxy) printf '{"weave_network":{"IPAddress":"172.31.20.2"}}\n' ;;
  weave-backend) printf '{"weave_network":{"IPAddress":"172.31.20.4"}}\n' ;;
  weave-nextcloud) printf '{"weave_network":{"IPAddress":"172.31.20.3"}}\n' ;;
  *) exit 1 ;;
esac
MOCK
chmod +x "${audit_bin}/docker"
container_actor_evidence="${TMP_DIR}/security-evidence-container-actor.json"
PATH="${audit_bin}:${PATH}" \
MOCK_SECURITY_LOG="${security_log}" \
WEAVE_AUDIT_HASH_SALT=fixture-salt \
TF_VAR_docker_network_name=weave_network \
TF_VAR_nextcloud_backend_actor_username='' \
WEAVE_NEXTCLOUD_BACKEND_ACTOR_USERNAME='' \
WEAVE_NEXTCLOUD_FILES_ACTOR_USERNAME='' \
  bash "${TEST_ROOT}/nextcloud-auth-security-audit.sh" --output "${container_actor_evidence}"
jq -e '
  .backendActorAttribution.configured == true and
  .backendActorAttribution.failureObserved == true and
  .backendActorAttribution.failureEvents == 2
' "${container_actor_evidence}" >/dev/null
if grep -Eq 'fixture-service-actor|UNRELATED_SECRET|must-not-appear' "${container_actor_evidence}"; then
  fail "container-derived actor classification leaked a raw environment value"
fi

credential_state="${TMP_DIR}/state/bootstrap.env"
mkdir -p "$(dirname -- "${BOOTSTRAP_ENV_FILE}")"
printf 'export TF_VAR_nextcloud_backend_actor_token=%q\n' 'stable-fixture-credential' >"${BOOTSTRAP_ENV_FILE}"
WEAVE_LOCAL_CREDENTIAL_STATE_FILE="${credential_state}"
export WEAVE_LOCAL_CREDENTIAL_STATE_FILE
persist_bootstrap_to_state
rm -f "${BOOTSTRAP_ENV_FILE}"
restore_persisted_bootstrap_from_state
grep -Fq 'stable-fixture-credential' "${BOOTSTRAP_ENV_FILE}" || fail "durable bootstrap credential state was not restored"
[[ "$(file_mode "${credential_state}")" == 600 ]] || fail "durable credential state must be mode 0600"

unset WEAVE_LOCAL_CREDENTIAL_STATE_FILE
export TF_VAR_isolated_e2e_enabled=true
if local_credential_state_file >/dev/null 2>&1; then
  fail "isolated E2E must not consume the persistent dogfood credential state"
fi

printf 'nextcloud health/readiness contract tests passed\n'
