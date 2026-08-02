#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=infra/weave-workspace/lib/runtime-namespace.sh
source "${ROOT_DIR}/lib/runtime-namespace.sh"
readonly ROOT_DIR
readonly CALENDAR_COLLECTION_HELPER="${ROOT_DIR}/lib/calendar-collection.sh"
# shellcheck disable=SC1090,SC1091
source "${CALENDAR_COLLECTION_HELPER}"
CALENDAR_ID=""

OPERATION=""
STATE_FILE="${WEAVE_E2E_CALENDAR_OUTAGE_STATE_FILE:-}"
STARTUP_ENV_FILE="${WEAVE_E2E_STARTUP_ENV_PATH:-}"
STACK_BOOTSTRAP_ENV="${WEAVE_E2E_STACK_BOOTSTRAP_ENV:-${ROOT_DIR}/.generated/bootstrap.env}"
OUTPUT_ROOT="${WEAVE_E2E_OUTPUT_ROOT:-${ROOT_DIR}/.generated/isolated-e2e}"
NEXTCLOUD_CONTAINER="${WEAVE_E2E_NEXTCLOUD_CONTAINER:-}"
BACKEND_CONTAINER="${WEAVE_E2E_BACKEND_CONTAINER:-}"
METRIC_TIMEOUT_SECONDS="${WEAVE_E2E_CALENDAR_OUTAGE_TIMEOUT_SECONDS:-240}"
METRIC_POLL_SECONDS="${WEAVE_E2E_CALENDAR_OUTAGE_POLL_SECONDS:-5}"

NAMESPACE=""
NETWORK=""
BACKEND_ACTOR=""
METRICS_URL=""
AUTO_RESTORE_ON_FAILURE="false"

fail() {
  printf 'ISOLATED_CALENDAR_OUTAGE_ERROR %s\n' "$*" >&2
  exit 1
}

usage() {
  cat <<'EOF'
Usage: isolated-e2e-calendar-outage.sh begin|restore [options]

Temporarily removes only the disposable Nextcloud backend actor's dedicated,
non-default workspace calendar and proves cached Calendar degradation without
affecting Files. The provider-default calendar is never used as the fault seam.

Options:
  --state-file PATH          Private run state/evidence file.
  --startup-env PATH         Prepared isolated Compose startup env.
  --stack-bootstrap-env PATH Private bootstrap env written by install.sh.

Required:
  WEAVE_E2E_STACK_SCOPE=isolated

Recovery command (safe to repeat):
  WEAVE_E2E_STACK_SCOPE=isolated \
    bash infra/weave-workspace/isolated-e2e-calendar-outage.sh restore

`begin` automatically recreates the calendar if deletion or cached-health
verification fails. A successful `begin` intentionally leaves the isolated
calendar unavailable until `restore` is run. `restore` is idempotent.
EOF
}

parse_args() {
  [[ $# -gt 0 ]] || { usage >&2; exit 2; }
  OPERATION="$1"
  shift
  case "${OPERATION}" in
    begin|restore) ;;
    -h|--help) usage; exit 0 ;;
    *) fail "operation must be begin or restore" ;;
  esac
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --state-file) STATE_FILE="${2:-}"; shift 2 ;;
      --startup-env) STARTUP_ENV_FILE="${2:-}"; shift 2 ;;
      --stack-bootstrap-env) STACK_BOOTSTRAP_ENV="${2:-}"; shift 2 ;;
      -h|--help) usage; exit 0 ;;
      *) fail "unknown argument" ;;
    esac
  done
}

require_bounded_integer() {
  local name="$1" value="$2" minimum="$3" maximum="$4"
  [[ "${value}" =~ ^[0-9]+$ ]] || fail "${name} must be an integer"
  ((value >= minimum && value <= maximum)) ||
    fail "${name} is outside the supported bound"
}

load_environment() {
  local requested_scope="${WEAVE_E2E_STACK_SCOPE:-}"
  if [[ -f "${STACK_BOOTSTRAP_ENV}" ]]; then
    # shellcheck disable=SC1090
    source "${STACK_BOOTSTRAP_ENV}"
  fi
  if [[ -n "${STARTUP_ENV_FILE}" ]]; then
    [[ -f "${STARTUP_ENV_FILE}" ]] || fail "isolated startup env is unavailable"
    # shellcheck disable=SC1090
    source "${STARTUP_ENV_FILE}"
  fi
  [[ -z "${requested_scope}" ]] || WEAVE_E2E_STACK_SCOPE="${requested_scope}"

  NAMESPACE="${WEAVE_ISOLATED_E2E_NAMESPACE:-}"
  WEAVE_E2E_RUN_NAMESPACE="${WEAVE_E2E_RUN_NAMESPACE:-${NAMESPACE}}"
  export WEAVE_E2E_RUN_NAMESPACE
  CALENDAR_ID="$(weave_backend_actor_workspace_calendar_id "${NAMESPACE}")"
  NETWORK="${WEAVE_DOCKER_NETWORK_NAME:-}"
  NEXTCLOUD_CONTAINER="${NEXTCLOUD_CONTAINER:-$(weave_container_name nextcloud)}"
  BACKEND_CONTAINER="${BACKEND_CONTAINER:-$(weave_container_name backend)}"
  BACKEND_ACTOR="${WEAVE_NEXTCLOUD_BACKEND_ACTOR_USERNAME:-${WEAVE_NEXTCLOUD_FILES_ACTOR_USERNAME:-}}"
  METRICS_URL="${WEAVE_E2E_ACTUATOR_METRICS_URL:-http://127.0.0.1:${WEAVE_BACKEND_HOST_PORT:-48084}/actuator/metrics}"
  STATE_FILE="${STATE_FILE:-${OUTPUT_ROOT}/${NAMESPACE}/calendar-outage-state.json}"
}

validate_loopback_metrics_url() {
  python3 - "${METRICS_URL}" <<'PY'
import ipaddress
import sys
from urllib.parse import urlparse

value = urlparse(sys.argv[1])
if (
    value.scheme != "http"
    or value.username is not None
    or value.password is not None
    or value.query
    or value.fragment
    or value.path.rstrip("/") != "/actuator/metrics"
):
    raise SystemExit(1)
try:
    address = ipaddress.ip_address(value.hostname or "")
except ValueError:
    raise SystemExit(1)
if not address.is_loopback:
    raise SystemExit(1)
PY
}

validate_state_path() {
  local output_real state_parent_real
  [[ ! -L "${STATE_FILE}" ]] || fail "fixture state file must not be a symbolic link"
  mkdir -p "${OUTPUT_ROOT}" "$(dirname -- "${STATE_FILE}")"
  output_real="$(cd "${OUTPUT_ROOT}" && pwd -P)"
  state_parent_real="$(cd "$(dirname -- "${STATE_FILE}")" && pwd -P)"
  [[ "${state_parent_real}" == "${output_real}" || "${state_parent_real}" == "${output_real}"/* ]] ||
    fail "fixture state file must remain below the isolated output root"
}

container_environment() {
  docker inspect --format '{{json .Config.Env}}' "$1"
}

container_networks() {
  docker inspect --format '{{json .NetworkSettings.Networks}}' "$1"
}

assert_isolated_runtime() {
  [[ "${WEAVE_E2E_STACK_SCOPE:-}" == isolated ]] || fail "explicit isolated stack scope is required"
  [[ "${WEAVE_ISOLATED_E2E_ENABLED:-false}" == true ]] || fail "isolated E2E infrastructure is not enabled"
  [[ "${NAMESPACE}" =~ ^weave-e2e-[0-9a-f]{16}$ ]] || fail "isolated namespace marker is invalid"
  [[ "${NETWORK}" == "${NAMESPACE}_network" ]] || fail "isolated Docker network marker does not match"
  [[ "${WEAVE_CREATE_TEST_USER:-false}" == false ]] || fail "static test-user mode is not allowed"
  [[ -z "${WEAVE_CONTEXT_AUTHORIZATION_DOGFOOD_PRINCIPAL_REF:-}" ]] || fail "persistent dogfood principal input is not allowed"
  [[ -n "${BACKEND_ACTOR}" ]] || fail "isolated backend actor is unavailable"

  local backend_env backend_networks nextcloud_networks
  backend_env="$(container_environment "${BACKEND_CONTAINER}")" || fail "isolated backend container is unavailable"
  backend_networks="$(container_networks "${BACKEND_CONTAINER}")" || fail "isolated backend network cannot be verified"
  nextcloud_networks="$(container_networks "${NEXTCLOUD_CONTAINER}")" || fail "isolated Nextcloud network cannot be verified"
  jq -e \
    --arg namespace "WEAVE_ISOLATED_E2E_NAMESPACE=${NAMESPACE}" \
    --arg actor "WEAVE_NEXTCLOUD_FILES_ACTOR_USERNAME=${BACKEND_ACTOR}" \
    --arg calendarPath "WEAVE_CALDAV_CALENDAR_PATH_TEMPLATE=$(weave_backend_actor_workspace_calendar_path "${BACKEND_ACTOR}" "${NAMESPACE}")" '
      index($namespace) != null and index($actor) != null and index($calendarPath) != null
    ' <<<"${backend_env}" >/dev/null || fail "backend runtime is not bound to the isolated actor calendar"
  jq -e --arg network "${NETWORK}" 'has($network)' <<<"${backend_networks}" >/dev/null ||
    fail "backend is not attached to the exact isolated network"
  jq -e --arg network "${NETWORK}" 'has($network)' <<<"${nextcloud_networks}" >/dev/null ||
    fail "Nextcloud is not attached to the exact isolated network"
}

sha256() {
  printf '%s' "$1" | shasum -a 256 | awk '{print $1}'
}

validate_existing_state() {
  [[ -f "${STATE_FILE}" ]] || return 0
  jq -e \
    --arg namespaceSha256 "$(sha256 "${NAMESPACE}")" \
    --arg actorSha256 "$(sha256 "${BACKEND_ACTOR}")" \
    --arg calendarSha256 "$(sha256 "${CALENDAR_ID}")" '
      .schemaVersion == "weave.isolated-calendar-outage-fixture.v2" and
      .namespaceSha256 == $namespaceSha256 and
      .actorSha256 == $actorSha256 and
      .calendarSha256 == $calendarSha256 and
      .persistentDogfoodEligible == false and
      .supportSafe == true
    ' "${STATE_FILE}" >/dev/null || fail "fixture state belongs to another runtime or is malformed"
}

write_state() {
  local state="$1" calendar_status="$2" files_status="$3" recovery_required="$4"
  local observed_at
  observed_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  umask 077
  jq -n \
    --arg state "${state}" \
    --arg observedAtUtc "${observed_at}" \
    --arg namespaceSha256 "$(sha256 "${NAMESPACE}")" \
    --arg actorSha256 "$(sha256 "${BACKEND_ACTOR}")" \
    --arg calendarSha256 "$(sha256 "${CALENDAR_ID}")" \
    --argjson calendarStatus "${calendar_status}" \
    --argjson filesStatus "${files_status}" \
    --argjson recoveryRequired "${recovery_required}" '
      {
        schemaVersion:"weave.isolated-calendar-outage-fixture.v2",
        state:$state,
        observedAtUtc:$observedAtUtc,
        namespaceSha256:$namespaceSha256,
        actorSha256:$actorSha256,
        calendarSha256:$calendarSha256,
        calendarCollectionKind:"dedicated-non-default",
        providerDefaultAutoProvisioningEligible:false,
        cachedHealth:{calendarStatus:$calendarStatus,filesStatus:$filesStatus},
        recoveryRequired:$recoveryRequired,
        persistentDogfoodEligible:false,
        credentialsIncluded:false,
        rawIdentityIncluded:false,
        rawProviderPayloadIncluded:false,
        supportSafe:true
      }
    ' >"${STATE_FILE}"
}

occ() {
  docker exec --user www-data "${NEXTCLOUD_CONTAINER}" php occ "$@"
}

delete_workspace_calendar() {
  occ dav:delete-calendar --force "${BACKEND_ACTOR}" "${CALENDAR_ID}" >/dev/null 2>&1
}

create_workspace_calendar() {
  local output
  if output="$(occ dav:create-calendar "${BACKEND_ACTOR}" "${CALENDAR_ID}" 2>&1)"; then
    return 0
  fi
  printf '%s' "${output}" | grep -Eiq 'already exists|calendar.*exists|duplicate'
}

metric_status() {
  local capability="$1" response
  response="$(curl --silent --fail --connect-timeout 5 --max-time 15 \
    "${METRICS_URL}/weave.provider.health.status?tag=capability:${capability}")" || return 1
  jq -er '
    [.measurements[]? | select(.statistic == "VALUE") | .value][0]
    | if . == 0 then "0" elif . == 1 then "1" elif . == 2 then "2" else error("noncanonical") end
  ' <<<"${response}"
}

poll_cached_health() {
  local expected_calendar="$1" attempts attempt calendar_status files_status
  attempts=$(((METRIC_TIMEOUT_SECONDS + METRIC_POLL_SECONDS - 1) / METRIC_POLL_SECONDS))
  for ((attempt = 1; attempt <= attempts; attempt++)); do
    calendar_status="$(metric_status calendar 2>/dev/null || printf unknown)"
    files_status="$(metric_status files 2>/dev/null || printf unknown)"
    if [[ "${files_status}" =~ ^[012]$ && "${files_status}" != 2 ]]; then
      fail "Files cached capability left the available state during the Calendar fixture"
    fi
    if [[ "${calendar_status}" == "${expected_calendar}" && "${files_status}" == 2 ]]; then
      printf '%s %s\n' "${calendar_status}" "${files_status}"
      return 0
    fi
    ((attempt == attempts)) || sleep "${METRIC_POLL_SECONDS}"
  done
  fail "cached provider health did not reach the required isolated fixture state"
}

automatic_recovery() {
  if create_workspace_calendar; then
    write_state recreated_after_failed_operation null null true
    printf 'CALENDAR_OUTAGE_FIXTURE_RECOVERY_RESULT status=calendar_recreated recoveryVerificationRequired=true supportSafe=true\n'
    return 0
  fi
  printf 'CALENDAR_OUTAGE_FIXTURE_RECOVERY_RESULT status=failed recoveryVerificationRequired=true supportSafe=true\n' >&2
  return 1
}

on_exit() {
  local status=$?
  trap - EXIT INT TERM
  if ((status != 0)) && [[ "${AUTO_RESTORE_ON_FAILURE}" == true ]]; then
    automatic_recovery || status=1
  fi
  exit "${status}"
}

begin_outage() {
  local state="" initial_calendar initial_files observed calendar_status files_status
  if [[ -f "${STATE_FILE}" ]]; then
    state="$(jq -r '.state // empty' "${STATE_FILE}")"
  fi
  trap on_exit EXIT
  trap 'exit 130' INT
  trap 'exit 143' TERM

  if [[ "${state}" != outage_active ]]; then
    initial_calendar="$(metric_status calendar 2>/dev/null || printf unknown)"
    initial_files="$(metric_status files 2>/dev/null || printf unknown)"
    [[ "${initial_calendar}:${initial_files}" == 2:2 ]] ||
      fail "isolated Calendar/Files cached health must start available"
    AUTO_RESTORE_ON_FAILURE=true
    delete_workspace_calendar || fail "isolated workspace calendar could not be removed"
    write_state outage_active null 2 true
  else
    AUTO_RESTORE_ON_FAILURE=true
  fi

  observed="$(poll_cached_health 0)"
  read -r calendar_status files_status <<<"${observed}"
  write_state outage_active "${calendar_status}" "${files_status}" true
  printf 'CALENDAR_OUTAGE_FIXTURE_RESULT operation=begin status=passed calendarStatus=0 filesStatus=2 isolated=true recoveryRequired=true supportSafe=true\n'
}

restore_outage() {
  local observed calendar_status files_status
  AUTO_RESTORE_ON_FAILURE=true
  trap on_exit EXIT
  trap 'exit 130' INT
  trap 'exit 143' TERM

  create_workspace_calendar || fail "isolated workspace calendar could not be recreated"
  observed="$(poll_cached_health 2)"
  read -r calendar_status files_status <<<"${observed}"
  write_state restored "${calendar_status}" "${files_status}" false
  AUTO_RESTORE_ON_FAILURE=false
  printf 'CALENDAR_OUTAGE_FIXTURE_RESULT operation=restore status=passed calendarStatus=2 filesStatus=2 isolated=true recoveryRequired=false supportSafe=true\n'
}

main() {
  parse_args "$@"
  command -v curl >/dev/null || fail "curl is required"
  command -v docker >/dev/null || fail "docker is required"
  command -v jq >/dev/null || fail "jq is required"
  command -v python3 >/dev/null || fail "python3 is required"
  command -v shasum >/dev/null || fail "shasum is required"
  require_bounded_integer WEAVE_E2E_CALENDAR_OUTAGE_TIMEOUT_SECONDS "${METRIC_TIMEOUT_SECONDS}" 1 900
  require_bounded_integer WEAVE_E2E_CALENDAR_OUTAGE_POLL_SECONDS "${METRIC_POLL_SECONDS}" 1 30
  load_environment
  validate_loopback_metrics_url || fail "Actuator metrics endpoint must be loopback-only"
  validate_state_path
  assert_isolated_runtime
  validate_existing_state
  case "${OPERATION}" in
    begin) begin_outage ;;
    restore) restore_outage ;;
  esac
}

main "$@"
