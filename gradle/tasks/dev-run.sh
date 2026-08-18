#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

REPOSITORY="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly REPOSITORY
readonly WORKSPACE="${REPOSITORY}/infra/weave-workspace"
readonly RUNNER="${REPOSITORY}/gradle/tasks/dev-host-process.py"
# shellcheck source=infra/weave-workspace/lib/runtime-namespace.sh
source "${WORKSPACE}/lib/runtime-namespace.sh"
export WEAVE_RESOURCE_PREFIX=weave-dev

SERVER_PID=
MCP_PID=
cleanup() {
  local status=$?
  trap - EXIT INT TERM
  [[ -z "${SERVER_PID}" ]] || kill "${SERVER_PID}" 2>/dev/null || true
  [[ -z "${MCP_PID}" ]] || kill "${MCP_PID}" 2>/dev/null || true
  wait "${SERVER_PID}" 2>/dev/null || true
  wait "${MCP_PID}" 2>/dev/null || true
  exit "${status}"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

python3 "${RUNNER}" \
  --component server \
  --container "$(weave_container_name backend)" \
  --host-port "${WEAVE_DEV_BACKEND_PORT:-58084}" \
  --repository "${REPOSITORY}" &
SERVER_PID=$!

python3 "${RUNNER}" \
  --component mcp \
  --container "$(weave_container_name mcp-server)" \
  --host-port "${WEAVE_DEV_MCP_PORT:-58085}" \
  --repository "${REPOSITORY}" &
MCP_PID=$!

printf 'WEAVE_DEV_RUN_STARTED serverPid=%s mcpPid=%s\n' "${SERVER_PID}" "${MCP_PID}"

while kill -0 "${SERVER_PID}" 2>/dev/null && kill -0 "${MCP_PID}" 2>/dev/null; do
  wait -n "${SERVER_PID}" "${MCP_PID}" || exit $?
done

wait "${SERVER_PID}"
wait "${MCP_PID}"
