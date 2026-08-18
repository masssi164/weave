#!/usr/bin/env bash
# shellcheck shell=bash

# Shared Compose naming helpers for the persistent dogfood stack and disposable
# live-E2E projects. This file is sourced by operator scripts; it intentionally
# does not change shell options or execute work at source time.

weave_runtime_fail() {
  printf 'WEAVE_RUNTIME_NAMESPACE_ERROR %s\n' "$*" >&2
  return 1
}

weave_isolated_e2e_enabled() {
  [[ "${WEAVE_E2E_STACK_SCOPE:-persistent}" == "isolated" ]]
}

weave_validate_isolated_namespace() {
  local namespace="${WEAVE_E2E_RUN_NAMESPACE:-}"

  [[ "${namespace}" =~ ^weave-e2e-[a-z0-9][a-z0-9-]{5,39}$ ]] ||
    weave_runtime_fail "isolated E2E requires a bounded weave-e2e-* namespace"
}

weave_validate_support_safe_evidence_url() {
  local value="${1:-}"

  command -v python3 >/dev/null 2>&1 ||
    weave_runtime_fail "python3 is required to validate candidate evidence URLs" || return 1

  python3 - "${value}" <<'PY'
import sys
from urllib.parse import urlsplit

value = sys.argv[1]
try:
    parsed = urlsplit(value)
    # Accessing port also rejects malformed/out-of-range port declarations.
    parsed.port
except (TypeError, ValueError):
    raise SystemExit(1)

valid = (
    parsed.scheme == "https"
    and bool(parsed.hostname)
    and parsed.username is None
    and parsed.password is None
    and not parsed.query
    and not parsed.fragment
    and "?" not in value
    and "#" not in value
    and "@" not in parsed.netloc
    and "%" not in parsed.netloc
    and "\\" not in value
    and all(ord(char) < 128 and not char.isspace() and ord(char) >= 0x20 for char in value)
)
raise SystemExit(0 if valid else 1)
PY
}

weave_resource_prefix() {
  if weave_isolated_e2e_enabled; then
    weave_validate_isolated_namespace || return 1
    printf '%s' "${WEAVE_E2E_RUN_NAMESPACE}"
    return
  fi

  printf '%s' "${WEAVE_RESOURCE_PREFIX:-weave}"
}

weave_volume_prefix() {
  weave_resource_prefix | tr '-' '_'
}

weave_container_name() {
  local suffix="$1"
  printf '%s-%s' "$(weave_resource_prefix)" "${suffix}"
}

weave_volume_name() {
  local suffix="$1"
  printf '%s_%s' "$(weave_volume_prefix)" "${suffix}"
}

weave_network_name() {
  if weave_isolated_e2e_enabled; then
    printf '%s_network' "$(weave_resource_prefix)"
    return
  fi

  printf '%s' "${WEAVE_DOCKER_NETWORK:-weave_network}"
}

weave_workspace_generated_dir() {
  local workspace_root="$1"
  if weave_isolated_e2e_enabled; then
    printf '%s/.generated/isolated/%s' \
      "${workspace_root}" "$(weave_resource_prefix)"
    return
  fi

  printf '%s' "${WEAVE_GENERATED_ROOT:-${workspace_root}/.generated/dev}"
}

weave_isolated_run_root() {
  weave_isolated_e2e_enabled ||
    weave_runtime_fail "isolated run root requested for a persistent stack" || return 1
  weave_validate_isolated_namespace || return 1

  local output_root="${WEAVE_E2E_OUTPUT_ROOT:-}"
  [[ "${output_root}" == /* ]] ||
    weave_runtime_fail "WEAVE_E2E_OUTPUT_ROOT must be an absolute path" || return 1

  printf '%s/%s' "${output_root%/}" "${WEAVE_E2E_RUN_NAMESPACE}"
}
