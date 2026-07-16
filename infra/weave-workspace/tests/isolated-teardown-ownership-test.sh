#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
TEARDOWN="${ROOT_DIR}/teardown.sh"
TMP_DIR="$(mktemp -d)"
MOCK_BIN="${TMP_DIR}/bin"
MOCK_STATE="${TMP_DIR}/state"
OUTPUT_ROOT="${TMP_DIR}/output"
NAMESPACE="weave-e2e-fedcba9876543210"
RUN_ID="ownership-fixture-run"
CANDIDATE="dddddddddddddddddddddddddddddddddddddddd"
EVIDENCE_REF="https://github.example.invalid/weave/actions/runs/202"
OWNERSHIP_FILE="${OUTPUT_ROOT}/${NAMESPACE}/teardown-ownership.json"
COMMAND_LOG="${MOCK_STATE}/commands.log"
trap 'rm -rf "${TMP_DIR}"' EXIT

fail() { printf '%s\n' "$*" >&2; exit 1; }

mkdir -p "${MOCK_BIN}" "${MOCK_STATE}" "$(dirname -- "${OWNERSHIP_FILE}")"
jq -n \
  --arg namespace "${NAMESPACE}" \
  --arg runId "${RUN_ID}" \
  --arg candidateCommit "${CANDIDATE}" \
  --arg candidateEvidenceRef "${EVIDENCE_REF}" \
  '{schemaVersion:"weave.isolated-e2e-teardown-ownership.v1",scope:"isolated",namespace:$namespace,runId:$runId,candidateCommit:$candidateCommit,candidateEvidenceRef:$candidateEvidenceRef,resourcePrefix:$namespace}' \
  >"${OWNERSHIP_FILE}"
chmod 600 "${OWNERSHIP_FILE}"
: >"${COMMAND_LOG}"
: >"${MOCK_STATE}/backend-present"

cat >"${MOCK_BIN}/tofu" <<'MOCK'
#!/usr/bin/env bash
printf 'tofu %s\n' "$*" >>"${MOCK_COMMAND_LOG}"
exit 1
MOCK
chmod +x "${MOCK_BIN}/tofu"

cat >"${MOCK_BIN}/docker" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >>"${MOCK_COMMAND_LOG}"

if [[ "$*" == *"weave-backend"* && "$*" != *"${MOCK_NAMESPACE}-backend"* ]]; then
  printf 'persistent resource was probed\n' >>"${MOCK_COMMAND_LOG}"
  exit 97
fi

if [[ "${1:-}" == container && "${2:-}" == inspect ]]; then
  if [[ "${3:-}" == --format ]]; then
    name="${5:-}"
    [[ "${name}" == "${MOCK_NAMESPACE}-backend" && -f "${MOCK_STATE}/backend-present" ]] || exit 1
    printf '%s\n' "${MOCK_RESOURCE_LABELS}"
    exit 0
  fi
  name="${3:-}"
  [[ "${name}" == "${MOCK_NAMESPACE}-backend" && -f "${MOCK_STATE}/backend-present" ]]
  exit
fi

if [[ "${1:-}" == volume || "${1:-}" == network ]]; then
  exit 1
fi

if [[ "${1:-}" == rm ]]; then
  name="${*: -1}"
  [[ "${name}" == "${MOCK_NAMESPACE}-backend" ]] || exit 98
  rm -f "${MOCK_STATE}/backend-present"
  exit 0
fi

exit 1
MOCK
chmod +x "${MOCK_BIN}/docker"

common_env=(
  PATH="${MOCK_BIN}:${PATH}"
  MOCK_COMMAND_LOG="${COMMAND_LOG}"
  MOCK_STATE="${MOCK_STATE}"
  MOCK_NAMESPACE="${NAMESPACE}"
  WEAVE_E2E_STACK_SCOPE=isolated
  WEAVE_E2E_RUN_ID="${RUN_ID}"
  WEAVE_E2E_OUTPUT_ROOT="${OUTPUT_ROOT}"
  WEAVE_CANDIDATE_COMMIT="${CANDIDATE}"
  WEAVE_CANDIDATE_EVIDENCE_REF="${EVIDENCE_REF}"
  WEAVE_TEARDOWN_OWNERSHIP_FILE="${OWNERSHIP_FILE}"
  WEAVE_REMOVE_VOLUMES=true
  TF_VAR_isolated_e2e_enabled=true
  TF_VAR_isolated_e2e_namespace="${NAMESPACE}"
  TF_VAR_docker_network_name="${NAMESPACE}_network"
)

set +e
env "${common_env[@]}" MOCK_RESOURCE_LABELS='persistent-dogfood|weave' \
  bash "${TEARDOWN}" >"${TMP_DIR}/mismatch.out" 2>&1
mismatch_status=$?
set -e
[[ "${mismatch_status}" == 1 ]] || fail "ownership mismatch must fail before deletion"
grep -Fq 'ownership labels do not match the isolated run' "${TMP_DIR}/mismatch.out" ||
  fail "ownership mismatch did not explain its refusal"
if grep -Eq '^rm ' "${COMMAND_LOG}"; then
  fail "ownership mismatch reached docker rm"
fi
if grep -Fq 'persistent resource was probed' "${COMMAND_LOG}"; then
  fail "isolated teardown probed a persistent dogfood resource"
fi

: >"${COMMAND_LOG}"
: >"${MOCK_STATE}/backend-present"
env "${common_env[@]}" MOCK_RESOURCE_LABELS="isolated|${NAMESPACE}" \
  bash "${TEARDOWN}" >"${TMP_DIR}/owned.out" 2>&1
grep -Fq "Removing container ${NAMESPACE}-backend" "${TMP_DIR}/owned.out" ||
  fail "matching run ownership did not remove the exact disposable container"
grep -Fq "rm -f -v ${NAMESPACE}-backend" "${COMMAND_LOG}" ||
  fail "matching run ownership did not issue the exact disposable removal"
if grep -Fq 'persistent resource was probed' "${COMMAND_LOG}" ||
  grep -Eq '(^|[[:space:]])weave_(db|keycloak|nextcloud|synapse|mailpit|caddy)' "${COMMAND_LOG}"; then
  fail "isolated teardown touched a persistent dogfood resource"
fi

state_root="${OUTPUT_ROOT}/${NAMESPACE}/runtime/opentofu/state"
mkdir -p "${state_root}"
: >"${state_root}/01-infrastructure.tfstate"
: >"${state_root}/02-keycloak-setup.tfstate"
: >"${COMMAND_LOG}"
: >"${MOCK_STATE}/backend-present"
set +e
env "${common_env[@]}" MOCK_RESOURCE_LABELS="isolated|${NAMESPACE}" \
  bash "${TEARDOWN}" >"${TMP_DIR}/destroy-failed.out" 2>&1
destroy_failed_status=$?
set -e
[[ "${destroy_failed_status}" == 1 ]] || fail "failed OpenTofu destroy must fail the isolated teardown"
grep -Fq 'run-owned state was retained for diagnosis' "${TMP_DIR}/destroy-failed.out" ||
  fail "failed OpenTofu destroy did not explain state retention"
[[ -f "${state_root}/01-infrastructure.tfstate" && -f "${state_root}/02-keycloak-setup.tfstate" ]] ||
  fail "failed OpenTofu destroy removed run-owned diagnostic state"
if grep -Fq ' state rm ' "${COMMAND_LOG}"; then
  fail "failed OpenTofu destroy rewrote the diagnostic state"
fi

printf 'isolated teardown ownership tests passed\n'
