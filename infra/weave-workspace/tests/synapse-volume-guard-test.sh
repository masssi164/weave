#!/usr/bin/env bash
# shellcheck shell=bash
# shellcheck disable=SC2016

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
readonly ROOT_DIR
readonly HELPER="${ROOT_DIR}/lib/synapse-volume.sh"
readonly INSTALL_SCRIPT="${ROOT_DIR}/install.sh"
readonly OPERATOR_CHECK="${ROOT_DIR}/operator-check.sh"

fail() {
  printf '%s\n' "$*" >&2
  exit 1
}

assert_contains() {
  local file="$1"
  local expected="$2"

  grep -Fq -- "${expected}" "${file}" || {
    printf 'Expected %s to contain: %s\n' "${file}" "${expected}" >&2
    cat "${file}" >&2
    exit 1
  }
}

assert_order() {
  local file="$1"
  local first="$2"
  local second="$3"
  local first_line
  local second_line

  first_line="$(grep -nF -- "${first}" "${file}" | head -1 | cut -d: -f1)"
  second_line="$(grep -nF -- "${second}" "${file}" | head -1 | cut -d: -f1)"
  [[ -n "${first_line}" && -n "${second_line}" && "${first_line}" -lt "${second_line}" ]] || \
    fail "Expected '${first}' to appear before '${second}' in ${file}"
}

with_fake_runtime() {
  local tmpdir="$1"
  mkdir -p "${tmpdir}/bin"

  cat > "${tmpdir}/bin/docker" <<'DOCKER'
#!/usr/bin/env bash
set -euo pipefail
printf 'docker %s\n' "$*" >> "${MOCK_COMMAND_LOG}"
case "${1:-}" in
  volume)
    if [[ "${2:-}" == "inspect" ]]; then
      [[ "${MOCK_DOCKER_VOLUME_EXISTS:-false}" == "true" ]]
      exit $?
    fi
    ;;
  run)
    if [[ "$*" == *'stat -c'* ]]; then
      printf '991:991 750 /data\n991:991 750 /data/media_store\n'
    fi
    exit "${MOCK_DOCKER_RUN_STATUS:-0}"
    ;;
esac
exit 0
DOCKER

  cat > "${tmpdir}/bin/tofu" <<'TOFU'
#!/usr/bin/env bash
set -euo pipefail
printf 'tofu %s\n' "$*" >> "${MOCK_COMMAND_LOG}"
args=("$@")
cmd_index=0
if [[ "${args[0]:-}" == -chdir=* ]]; then
  cmd_index=1
fi
cmd="${args[${cmd_index}]:-}"
sub="${args[$((cmd_index + 1))]:-}"
address="${args[$((cmd_index + 2))]:-}"
if [[ "${cmd}" == "state" && "${sub}" == "show" ]]; then
  case "${address}" in
    module.matrix.docker_volume.synapse_data)
      [[ "${MOCK_TF_HAS_VOLUME:-false}" == "true" ]]
      exit $?
      ;;
    module.matrix.terraform_data.synapse_volume_permissions)
      [[ "${MOCK_TF_HAS_PERMISSION:-false}" == "true" ]]
      exit $?
      ;;
  esac
fi
exit 0
TOFU

  chmod +x "${tmpdir}/bin/docker" "${tmpdir}/bin/tofu"
}

run_helper_case() {
  local action="$1"
  local tmpdir
  tmpdir="$(mktemp -d)"
  with_fake_runtime "${tmpdir}"
  export MOCK_COMMAND_LOG="${tmpdir}/commands.log"
  : > "${MOCK_COMMAND_LOG}"

  (
    export PATH="${tmpdir}/bin:${PATH}"
    export INFRA_DIR="${ROOT_DIR}/01-infrastructure"
    export TF_VAR_synapse_uid=991
    export TF_VAR_synapse_gid=991
    export TF_VAR_matrix_subdomain=matrix
    export TF_VAR_tenant_domain=weave.test
    # shellcheck disable=SC1090
    source "${HELPER}"
    case "${action}" in
      reconcile)
        synapse_reconcile_terraform_state
        ;;
      repair_verify)
        synapse_repair_volume_permissions
        synapse_verify_volume_writable
        ;;
      operator)
        synapse_operator_diagnose_volume
        ;;
      *)
        fail "Unknown helper action: ${action}"
        ;;
    esac
  ) > "${tmpdir}/stdout.log" 2> "${tmpdir}/stderr.log"

  printf '%s\n' "${tmpdir}"
}

case_missing_volume_stale_state() {
  export MOCK_DOCKER_VOLUME_EXISTS=false
  export MOCK_TF_HAS_VOLUME=true
  export MOCK_TF_HAS_PERMISSION=true
  local tmpdir
  tmpdir="$(run_helper_case reconcile)"
  assert_contains "${tmpdir}/commands.log" "tofu -chdir=${ROOT_DIR}/01-infrastructure state rm module.matrix.terraform_data.synapse_volume_permissions"
  assert_contains "${tmpdir}/commands.log" "tofu -chdir=${ROOT_DIR}/01-infrastructure state rm module.matrix.docker_volume.synapse_data"
  assert_contains "${tmpdir}/stdout.log" "volume weave_synapse_data is missing while OpenTofu state still records it"
  rm -rf "${tmpdir}"
}

case_existing_volume_repaired_and_verified() {
  export MOCK_DOCKER_VOLUME_EXISTS=true
  export MOCK_TF_HAS_VOLUME=true
  export MOCK_TF_HAS_PERMISSION=true
  local tmpdir
  tmpdir="$(run_helper_case repair_verify)"
  assert_contains "${tmpdir}/commands.log" "docker run --rm -u 0:0"
  assert_contains "${tmpdir}/commands.log" "docker run --rm -u 991:991"
  assert_contains "${tmpdir}/commands.log" "weave_synapse_data:/data"
  rm -rf "${tmpdir}"
}

case_operator_mentions_weave_not_homelab() {
  export MOCK_DOCKER_VOLUME_EXISTS=true
  export MOCK_TF_HAS_VOLUME=true
  export MOCK_TF_HAS_PERMISSION=true
  local tmpdir
  tmpdir="$(run_helper_case operator)"
  assert_contains "${tmpdir}/stdout.log" "weave-synapse"
  assert_contains "${tmpdir}/stdout.log" "not homelab-synapse"
  assert_contains "${tmpdir}/stdout.log" "991:991 750 /data"
  rm -rf "${tmpdir}"
}

main() {
  [[ -f "${HELPER}" ]] || fail "Missing helper: ${HELPER}"

  assert_contains "${INSTALL_SCRIPT}" 'source "${SYNAPSE_VOLUME_HELPER}"'
  assert_order "${INSTALL_SCRIPT}" 'synapse_reconcile_terraform_state' 'terraform_apply "${INFRA_DIR}"'
  assert_order "${INSTALL_SCRIPT}" 'terraform_apply "${INFRA_DIR}"' 'synapse_verify_volume_writable'
  assert_contains "${OPERATOR_CHECK}" 'synapse_operator_diagnose_volume'
  assert_contains "${HELPER}" 'not homelab-synapse'
  assert_contains "${HELPER}" 'Run ./install.sh to reconcile stale OpenTofu state and repair the volume'

  case_missing_volume_stale_state
  case_existing_volume_repaired_and_verified
  case_operator_mentions_weave_not_homelab

  printf '%s\n' 'synapse volume guard tests passed'
}

main "$@"
