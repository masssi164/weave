#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT="${ROOT_DIR}/isolated-e2e-calendar-outage.sh"
# shellcheck disable=SC1090,SC1091
source "${ROOT_DIR}/lib/calendar-collection.sh"
TMP_DIR="$(mktemp -d)"
MOCK_BIN="${TMP_DIR}/bin"
MOCK_STATE="${TMP_DIR}/state"
OUTPUT_ROOT="${TMP_DIR}/output"
NAMESPACE="weave-e2e-0123456789abcdef"
NETWORK="${NAMESPACE}_network"
ACTOR="fixture-backend-actor"
CALENDAR_ID="weave-workspace-${NAMESPACE}"
STATE_FILE="${OUTPUT_ROOT}/${NAMESPACE}/calendar-outage-state.json"
trap 'rm -rf "${TMP_DIR}"' EXIT

fail() { printf '%s\n' "$*" >&2; exit 1; }
sha256() { printf '%s' "$1" | shasum -a 256 | awk '{print $1}'; }

[[ "$(weave_backend_actor_workspace_calendar_id '')" == weave-workspace ]] ||
  fail "persistent workspace collection mapping changed"
[[ "$(weave_backend_actor_workspace_calendar_id "${NAMESPACE}")" == "${CALENDAR_ID}" ]] ||
  fail "isolated workspace collection mapping is not dedicated"
[[ "$(weave_backend_actor_workspace_calendar_path "${ACTOR}" "${NAMESPACE}")" == "/remote.php/dav/calendars/${ACTOR}/${CALENDAR_ID}/" ]] ||
  fail "isolated workspace collection path is inconsistent"

mkdir -p "${MOCK_BIN}" "${MOCK_STATE}" "${OUTPUT_ROOT}"
printf 'true\n' >"${MOCK_STATE}/calendar-present"
printf '2\n' >"${MOCK_STATE}/files-status"
: >"${MOCK_STATE}/commands.log"

startup_env="${TMP_DIR}/startup.env"
cat >"${startup_env}" <<ENV
export WEAVE_ISOLATED_E2E_ENABLED=true
export WEAVE_ISOLATED_E2E_NAMESPACE=${NAMESPACE}
export WEAVE_DOCKER_NETWORK_NAME=${NETWORK}
export WEAVE_CREATE_TEST_USER=false
export WEAVE_CONTEXT_AUTHORIZATION_DOGFOOD_PRINCIPAL_REF=''
ENV

bootstrap_env="${TMP_DIR}/bootstrap.env"
cat >"${bootstrap_env}" <<ENV
export WEAVE_BACKEND_HOST_PORT=48084
export WEAVE_NEXTCLOUD_BACKEND_ACTOR_USERNAME=${ACTOR}
export WEAVE_NEXTCLOUD_BACKEND_ACTOR_TOKEN=fixture-secret-never-output
ENV

cat >"${MOCK_BIN}/docker" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail

if [[ "${1:-}" == inspect ]]; then
  template="${3:-}"
  container="${4:-}"
  if [[ "${template}" == '{{json .Config.Env}}' && "${container}" == "${MOCK_BACKEND_CONTAINER}" ]]; then
    calendar_id="${MOCK_BACKEND_CALENDAR_ID:-${MOCK_CALENDAR_ID}}"
    jq -cn \
      --arg namespace "WEAVE_ISOLATED_E2E_NAMESPACE=${MOCK_NAMESPACE}" \
      --arg actor "WEAVE_NEXTCLOUD_FILES_ACTOR_USERNAME=${MOCK_ACTOR}" \
      --arg path "WEAVE_CALDAV_CALENDAR_PATH_TEMPLATE=/remote.php/dav/calendars/${MOCK_ACTOR}/${calendar_id}/" \
      '[$namespace,$actor,$path]'
    exit 0
  fi
  if [[ "${template}" == '{{json .NetworkSettings.Networks}}' ]]; then
    jq -cn --arg network "${MOCK_NETWORK}" '{($network):{IPAddress:"172.31.22.2"}}'
    exit 0
  fi
  exit 1
fi

if [[ "${1:-}" != exec ]]; then
  exit 1
fi
printf '%s\n' "$*" >>"${MOCK_STATE}/commands.log"
expected_prefix="exec --user www-data ${MOCK_NEXTCLOUD_CONTAINER} php occ"
case "$*" in
  "${expected_prefix} dav:delete-calendar --force ${MOCK_ACTOR} ${MOCK_CALENDAR_ID}")
    if [[ "$(cat "${MOCK_STATE}/calendar-present")" != true ]]; then
      exit 1
    fi
    printf 'false\n' >"${MOCK_STATE}/calendar-present"
    ;;
  "${expected_prefix} dav:create-calendar ${MOCK_ACTOR} ${MOCK_CALENDAR_ID}")
    if [[ "${MOCK_FORCE_CREATE_FAILURE:-false}" == true ]]; then
      exit 1
    fi
    if [[ "$(cat "${MOCK_STATE}/calendar-present")" == true ]]; then
      printf 'Calendar already exists\n'
      exit 1
    fi
    printf 'true\n' >"${MOCK_STATE}/calendar-present"
    ;;
  *)
    exit 1
    ;;
esac
MOCK
chmod +x "${MOCK_BIN}/docker"

cat >"${MOCK_BIN}/curl" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
url="${*: -1}"
if [[ "${url}" == *'capability:calendar' ]]; then
  if [[ "$(cat "${MOCK_STATE}/calendar-present")" == true ]]; then
    value=2
  else
    value=0
  fi
elif [[ "${url}" == *'capability:files' ]]; then
  value="$(cat "${MOCK_STATE}/files-status")"
  if [[ "${MOCK_FILES_DEGRADE_AFTER_DELETE:-false}" == true && "$(cat "${MOCK_STATE}/calendar-present")" != true ]]; then
    value=1
  fi
else
  exit 1
fi
jq -cn --argjson value "${value}" '{measurements:[{statistic:"VALUE",value:$value}]}'
MOCK
chmod +x "${MOCK_BIN}/curl"

cat >"${MOCK_BIN}/sleep" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
printf 'sleep:%s\n' "${1:-}" >>"${MOCK_STATE}/commands.log"
MOCK
chmod +x "${MOCK_BIN}/sleep"

export MOCK_STATE MOCK_NAMESPACE="${NAMESPACE}" MOCK_NETWORK="${NETWORK}" MOCK_ACTOR="${ACTOR}" MOCK_CALENDAR_ID="${CALENDAR_ID}"
export MOCK_BACKEND_CONTAINER="${NAMESPACE}-backend" MOCK_NEXTCLOUD_CONTAINER="${NAMESPACE}-nextcloud"
common_env=(
  PATH="${MOCK_BIN}:${PATH}"
  WEAVE_E2E_STACK_SCOPE=isolated
  WEAVE_E2E_OUTPUT_ROOT="${OUTPUT_ROOT}"
  WEAVE_E2E_STARTUP_ENV_PATH="${startup_env}"
  WEAVE_E2E_STACK_BOOTSTRAP_ENV="${bootstrap_env}"
  WEAVE_E2E_CALENDAR_OUTAGE_STATE_FILE="${STATE_FILE}"
  WEAVE_E2E_CALENDAR_OUTAGE_TIMEOUT_SECONDS=1
  WEAVE_E2E_CALENDAR_OUTAGE_POLL_SECONDS=1
)

if env "${common_env[@]}" WEAVE_E2E_STACK_SCOPE=persistent-dogfood \
  bash "${SCRIPT}" begin >/dev/null 2>&1; then
  fail "persistent dogfood scope must not run the Calendar outage fixture"
fi
[[ ! -s "${MOCK_STATE}/commands.log" ]] || fail "persistent-scope rejection touched the isolated provider"

default_calendar_output="$(env "${common_env[@]}" MOCK_BACKEND_CALENDAR_ID=personal \
  bash "${SCRIPT}" begin 2>&1 || true)"
grep -Fq 'ISOLATED_CALENDAR_OUTAGE_ERROR backend runtime is not bound to the isolated actor calendar' \
  <<<"${default_calendar_output}"
[[ ! -s "${MOCK_STATE}/commands.log" ]] || fail "provider-default Calendar rejection touched the isolated provider"

begin_output="$(env "${common_env[@]}" bash "${SCRIPT}" begin)"
grep -Fqx 'CALENDAR_OUTAGE_FIXTURE_RESULT operation=begin status=passed calendarStatus=0 filesStatus=2 isolated=true recoveryRequired=true supportSafe=true' <<<"${begin_output}"
[[ "$(cat "${MOCK_STATE}/calendar-present")" == false ]] || fail "begin did not remove the isolated workspace calendar"
[[ "$(grep -Fc "dav:delete-calendar --force ${ACTOR} ${CALENDAR_ID}" "${MOCK_STATE}/commands.log")" == 1 ]] ||
  fail "begin did not issue exactly one scoped calendar deletion"
jq -e \
  --arg namespaceHash "$(sha256 "${NAMESPACE}")" \
  --arg actorHash "$(sha256 "${ACTOR}")" '
    .schemaVersion == "weave.isolated-calendar-outage-fixture.v2" and
    .state == "outage_active" and
    .namespaceSha256 == $namespaceHash and
    .actorSha256 == $actorHash and
    .calendarCollectionKind == "dedicated-non-default" and
    .providerDefaultAutoProvisioningEligible == false and
    .cachedHealth.calendarStatus == 0 and
    .cachedHealth.filesStatus == 2 and
    .recoveryRequired == true and
    .persistentDogfoodEligible == false and
    .supportSafe == true
  ' "${STATE_FILE}" >/dev/null

repeat_begin="$(env "${common_env[@]}" bash "${SCRIPT}" begin)"
grep -Fqx 'CALENDAR_OUTAGE_FIXTURE_RESULT operation=begin status=passed calendarStatus=0 filesStatus=2 isolated=true recoveryRequired=true supportSafe=true' <<<"${repeat_begin}"
[[ "$(grep -Fc 'dav:delete-calendar' "${MOCK_STATE}/commands.log")" == 1 ]] ||
  fail "repeated begin deleted a second calendar"

restore_output="$(env "${common_env[@]}" bash "${SCRIPT}" restore)"
grep -Fqx 'CALENDAR_OUTAGE_FIXTURE_RESULT operation=restore status=passed calendarStatus=2 filesStatus=2 isolated=true recoveryRequired=false supportSafe=true' <<<"${restore_output}"
[[ "$(cat "${MOCK_STATE}/calendar-present")" == true ]] || fail "restore did not recreate the isolated workspace calendar"
jq -e '.state == "restored" and .cachedHealth.calendarStatus == 2 and .cachedHealth.filesStatus == 2 and .recoveryRequired == false' \
  "${STATE_FILE}" >/dev/null

repeat_restore="$(env "${common_env[@]}" bash "${SCRIPT}" restore)"
grep -Fqx 'CALENDAR_OUTAGE_FIXTURE_RESULT operation=restore status=passed calendarStatus=2 filesStatus=2 isolated=true recoveryRequired=false supportSafe=true' <<<"${repeat_restore}"

if grep -Eq "${ACTOR}|fixture-secret-never-output|127\.0\.0\.1|actuator/metrics|remote\.php" "${STATE_FILE}" ||
  grep -Eq "${ACTOR}|fixture-secret-never-output|127\.0\.0\.1|actuator/metrics|remote\.php" \
    <<<"${begin_output}${restore_output}${repeat_restore}"; then
  fail "Calendar fixture emitted a username, credential, or URL"
fi

# If Files leaves available after deletion, begin must fail and its EXIT trap
# must recreate the exact dedicated workspace calendar before returning.
: >"${MOCK_STATE}/commands.log"
failure_output="$(env "${common_env[@]}" MOCK_FILES_DEGRADE_AFTER_DELETE=true \
  bash "${SCRIPT}" begin 2>&1 || true)"
grep -Fq 'ISOLATED_CALENDAR_OUTAGE_ERROR Files cached capability left the available state' <<<"${failure_output}"
grep -Fq 'CALENDAR_OUTAGE_FIXTURE_RECOVERY_RESULT status=calendar_recreated recoveryVerificationRequired=true supportSafe=true' <<<"${failure_output}"
[[ "$(cat "${MOCK_STATE}/calendar-present")" == true ]] || fail "failed begin left the workspace calendar missing"
[[ "$(grep -Fc 'dav:delete-calendar' "${MOCK_STATE}/commands.log")" == 1 ]] || fail "failure fixture did not delete exactly once"
[[ "$(grep -Fc 'dav:create-calendar' "${MOCK_STATE}/commands.log")" == 1 ]] || fail "failure trap did not recreate exactly once"
jq -e '.state == "recreated_after_failed_operation" and .recoveryRequired == true' "${STATE_FILE}" >/dev/null

env "${common_env[@]}" bash "${SCRIPT}" restore >/dev/null
[[ "$(cat "${MOCK_STATE}/calendar-present")" == true ]] || fail "documented restore command is not idempotent"

if grep -Evq "dav:(delete|create)-calendar.* ${CALENDAR_ID}$" "${MOCK_STATE}/commands.log"; then
  unexpected="$(grep -Ev "dav:(delete|create)-calendar.* ${CALENDAR_ID}$" "${MOCK_STATE}/commands.log" || true)"
  [[ -z "${unexpected}" ]] || fail "fixture touched a calendar other than its dedicated workspace collection"
fi

if grep -Eq "${ACTOR}|fixture-secret-never-output|https?://|actuator/metrics|remote\.php" <<<"${failure_output}"; then
  fail "failure marker leaked a username, credential, or URL"
fi

printf 'isolated Calendar outage fixture tests passed\n'
