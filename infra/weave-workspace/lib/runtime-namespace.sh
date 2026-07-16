#!/usr/bin/env bash
# shellcheck shell=bash

# Shared naming and OpenTofu-state helpers for the persistent dogfood stack and
# disposable live-E2E namespaces. This file is sourced by operator scripts; it
# intentionally does not change shell options or execute work at source time.

weave_runtime_fail() {
  printf 'WEAVE_RUNTIME_NAMESPACE_ERROR %s\n' "$*" >&2
  return 1
}

weave_isolated_e2e_enabled() {
  [[ "${TF_VAR_isolated_e2e_enabled:-false}" == "true" ]]
}

weave_validate_isolated_namespace() {
  local namespace="${TF_VAR_isolated_e2e_namespace:-}"

  [[ "${namespace}" =~ ^weave-e2e-[a-z0-9][a-z0-9-]{5,47}$ ]] ||
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
    printf '%s' "${TF_VAR_isolated_e2e_namespace}"
    return
  fi

  printf 'weave'
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

  printf 'weave_network'
}

weave_workspace_generated_dir() {
  local workspace_root="$1"
  if weave_isolated_e2e_enabled; then
    printf '%s/.generated/isolated/%s' \
      "${workspace_root}" "$(weave_resource_prefix)"
    return
  fi

  printf '%s/.generated' "${workspace_root}"
}

weave_infra_generated_dir() {
  local workspace_root="$1"
  if weave_isolated_e2e_enabled; then
    printf '%s/01-infrastructure/.generated/isolated/%s' \
      "${workspace_root}" "$(weave_resource_prefix)"
    return
  fi

  printf '%s/01-infrastructure/.generated' "${workspace_root}"
}

weave_iac_stage_name() {
  local dir="$1"
  case "$(basename -- "${dir}")" in
    01-infrastructure) printf '01-infrastructure' ;;
    02-keycloak-setup) printf '02-keycloak-setup' ;;
    *) weave_runtime_fail "unsupported OpenTofu stage: ${dir}" ;;
  esac
}

weave_isolated_run_root() {
  weave_isolated_e2e_enabled ||
    weave_runtime_fail "isolated run root requested for a persistent stack" || return 1
  weave_validate_isolated_namespace || return 1

  local output_root="${WEAVE_E2E_OUTPUT_ROOT:-}"
  [[ "${output_root}" == /* ]] ||
    weave_runtime_fail "WEAVE_E2E_OUTPUT_ROOT must be an absolute path" || return 1

  printf '%s/%s' "${output_root%/}" "${TF_VAR_isolated_e2e_namespace}"
}

weave_iac_state_file() {
  local dir="$1"
  local stage
  stage="$(weave_iac_stage_name "${dir}")" || return 1
  printf '%s/runtime/opentofu/state/%s.tfstate' \
    "$(weave_isolated_run_root)" "${stage}"
}

weave_iac_data_dir() {
  local dir="$1"
  local stage
  stage="$(weave_iac_stage_name "${dir}")" || return 1
  printf '%s/runtime/opentofu/data/%s' \
    "$(weave_isolated_run_root)" "${stage}"
}

weave_iac() {
  local dir="$1"
  shift

  if ! weave_isolated_e2e_enabled; then
    "${WEAVE_IAC_BIN:-tofu}" -chdir="${dir}" "$@"
    return
  fi

  local data_dir
  data_dir="$(weave_iac_data_dir "${dir}")" || return 1
  mkdir -p "${data_dir}" "$(dirname -- "$(weave_iac_state_file "${dir}")")"
  chmod 700 "$(weave_isolated_run_root)/runtime" \
    "$(weave_isolated_run_root)/runtime/opentofu" \
    "$(weave_isolated_run_root)/runtime/opentofu/data" \
    "$(weave_isolated_run_root)/runtime/opentofu/state" \
    "${data_dir}"
  TF_DATA_DIR="${data_dir}" "${WEAVE_IAC_BIN:-tofu}" -chdir="${dir}" "$@"
}

weave_iac_init() {
  local dir="$1"
  shift

  if ! weave_isolated_e2e_enabled; then
    weave_iac "${dir}" init "$@"
    return
  fi

  weave_iac "${dir}" init \
    -reconfigure \
    -backend-config="path=$(weave_iac_state_file "${dir}")" \
    "$@"
}
