#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

REPOSITORY="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly REPOSITORY
readonly WORKSPACE="${REPOSITORY}/infra/weave-workspace"
readonly BOOTSTRAP_ENV="${WORKSPACE}/.generated/bootstrap.env"
readonly RUNNER="${REPOSITORY}/gradle/tasks/dev-host-process.py"
# shellcheck source=infra/weave-workspace/lib/runtime-namespace.sh
source "${WORKSPACE}/lib/runtime-namespace.sh"

[[ -f "${BOOTSTRAP_ENV}" ]] || {
  printf 'WEAVE_DEV_RUN_ERROR bootstrap state is missing; run ./gradlew devUp\n' >&2
  exit 1
}
# shellcheck disable=SC1090
source "${BOOTSTRAP_ENV}"

[[ "${TF_VAR_deployment_environment:-}" == dev ]] || {
  printf 'WEAVE_DEV_RUN_ERROR bootstrap environment is not dev\n' >&2
  exit 1
}
[[ "${TF_VAR_application_runtime_mode:-}" == host ]] || {
  printf 'WEAVE_DEV_RUN_ERROR infrastructure is not configured for host applications; run ./gradlew devUp\n' >&2
  exit 1
}

PRIVATE_RUNTIME_DIR="$(mktemp -d)"
readonly PRIVATE_RUNTIME_DIR
chmod 700 "${PRIVATE_RUNTIME_DIR}"
printf '%s' "${TF_VAR_matrix_chat_appservice_as_token:?missing Matrix AS token}" \
  >"${PRIVATE_RUNTIME_DIR}/matrix-as.token"
printf '%s' "${TF_VAR_matrix_chat_appservice_hs_token:?missing Matrix HS token}" \
  >"${PRIVATE_RUNTIME_DIR}/matrix-hs.token"
chmod 600 "${PRIVATE_RUNTIME_DIR}/matrix-as.token" "${PRIVATE_RUNTIME_DIR}/matrix-hs.token"
export WEAVE_CHAT_MATRIX_APPSERVICE_AS_TOKEN_FILE="${PRIVATE_RUNTIME_DIR}/matrix-as.token"
export WEAVE_CHAT_MATRIX_APPSERVICE_HS_TOKEN_FILE="${PRIVATE_RUNTIME_DIR}/matrix-hs.token"

SERVER_PID=
MCP_PID=
cleanup() {
  local status=$?
  trap - EXIT INT TERM
  [[ -z "${SERVER_PID}" ]] || kill "${SERVER_PID}" 2>/dev/null || true
  [[ -z "${MCP_PID}" ]] || kill "${MCP_PID}" 2>/dev/null || true
  wait "${SERVER_PID}" 2>/dev/null || true
  wait "${MCP_PID}" 2>/dev/null || true
  rm -rf -- "${PRIVATE_RUNTIME_DIR}"
  exit "${status}"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

python3 "${RUNNER}" \
  --component server \
  --container "$(weave_container_name backend)" \
  --host-port "${TF_VAR_backend_host_port:-48084}" \
  --repository "${REPOSITORY}" &
SERVER_PID=$!

python3 "${RUNNER}" \
  --component mcp \
  --container "$(weave_container_name mcp-server)" \
  --host-port "${TF_VAR_mcp_host_port:-48085}" \
  --repository "${REPOSITORY}" &
MCP_PID=$!

printf 'WEAVE_DEV_RUN_STARTED serverPid=%s mcpPid=%s\n' "${SERVER_PID}" "${MCP_PID}"

while kill -0 "${SERVER_PID}" 2>/dev/null && kill -0 "${MCP_PID}" 2>/dev/null; do
  wait -n "${SERVER_PID}" "${MCP_PID}" || exit $?
done

wait "${SERVER_PID}"
wait "${MCP_PID}"
