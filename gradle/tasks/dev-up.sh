#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

REPOSITORY="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly REPOSITORY
readonly WORKSPACE="${REPOSITORY}/infra/weave-workspace"
# shellcheck source=infra/weave-workspace/lib/runtime-namespace.sh
source "${WORKSPACE}/lib/runtime-namespace.sh"

export TF_VAR_deployment_environment=dev
export TF_VAR_application_runtime_mode=host
export TF_VAR_resource_stack=weave
export TF_VAR_isolated_e2e_enabled=false
export TF_VAR_isolated_e2e_namespace=

bash "${WORKSPACE}/install.sh"

stop_host_replaced_container() {
  local name="$1"
  local component="$2"
  local labels

  labels="$(docker container inspect --format \
    '{{ index .Config.Labels "com.massimotter.weave.managed" }}|{{ index .Config.Labels "com.massimotter.weave.environment" }}|{{ index .Config.Labels "com.massimotter.weave.stack" }}|{{ index .Config.Labels "com.massimotter.weave.component" }}' \
    "${name}")"
  [[ "${labels}" == "true|dev|weave|${component}" ]] || {
    printf 'WEAVE_DEV_UP_ERROR refusing to stop %s: ownership labels are %s\n' \
      "${name}" "${labels}" >&2
    exit 1
  }
  docker container stop "${name}" >/dev/null
}

stop_host_replaced_container "$(weave_container_name backend)" server
stop_host_replaced_container "$(weave_container_name mcp-server)" mcp

printf '%s\n' \
  "WEAVE_DEV_UP_READY providers=containers applications=host" \
  "Run ./gradlew devRun to start Server and MCP as separate host processes."
