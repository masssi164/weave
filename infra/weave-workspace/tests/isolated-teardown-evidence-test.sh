#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
readonly ROOT_DIR
readonly TEARDOWN_SCRIPT="${ROOT_DIR}/teardown.sh"

test_root="$(mktemp -d)"
mock_bin="${test_root}/bin"
state_dir="${test_root}/state"
evidence_file="${test_root}/teardown.json"
identity_manifest="${test_root}/identity-manifest.json"
run_id="teardown-proof-fixture"
namespace="weave-e2e-$(printf '%s' "${run_id}" | shasum -a 256 | awk '{print substr($1,1,16)}')"
network_name="${namespace}_network"
output_root="${test_root}/output"
run_root="${output_root}/${namespace}"
ownership_file="${run_root}/teardown-ownership.json"
proof_token="${run_root}/chat-provider-proof.token"
candidate_commit="0123456789abcdef0123456789abcdef01234567"
candidate_evidence_ref="https://github.example.invalid/weave/actions/runs/86"
mkdir -p "${mock_bin}" "${state_dir}/container" "${state_dir}/network" "${state_dir}/volume" "${run_root}"
trap 'rm -rf "${test_root}"' EXIT

cat >"${mock_bin}/docker" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
state="${MOCK_DOCKER_STATE:?}"
kind="${1:-}"

if [[ "${kind}" == "inspect" ]]; then
  name="${@: -1}"
  [[ -f "${state}/container/${name}" ]] || exit 1
  printf '%s\n' "${MOCK_DOCKER_NETWORK:?}"
  exit 0
fi

action="${2:-}"
name="${@: -1}"
case "${kind}:${action}" in
  container:inspect|network:inspect|volume:inspect)
    [[ -f "${state}/${kind}/${name}" ]] || exit 1
    if [[ "${3:-}" == --format ]]; then
      printf 'isolated|%s\n' "${MOCK_NAMESPACE:?}"
    fi
    ;;
  network:rm)
    rm -f "${state}/network/${name}"
    ;;
  volume:rm)
    if [[ "${name}" != "${MOCK_PRESERVE_VOLUME:-}" ]]; then
      rm -f "${state}/volume/${name}"
    fi
    ;;
  rm:-f)
    rm -f "${state}/container/${name}"
    ;;
  *)
    printf 'unexpected mock docker command:' >&2
    printf ' %q' "$@" >&2
    printf '\n' >&2
    exit 2
    ;;
esac
MOCK
chmod +x "${mock_bin}/docker"

cat >"${mock_bin}/tofu" <<'MOCK'
#!/usr/bin/env bash
# No OpenTofu state exists in this isolated unit fixture.
exit 1
MOCK
chmod +x "${mock_bin}/tofu"

containers=(
  "${namespace}-proxy" "${namespace}-keycloak" "${namespace}-backend"
  "${namespace}-mas" "${namespace}-synapse" "${namespace}-nextcloud"
  "${namespace}-db" "${namespace}-mcp-server" "${namespace}-mailpit"
)
volume_prefix="${namespace//-/_}"
volumes=(
  "${volume_prefix}_caddy_data" "${volume_prefix}_caddy_config"
  "${volume_prefix}_db_data" "${volume_prefix}_keycloak_data"
  "${volume_prefix}_mailpit_data" "${volume_prefix}_nextcloud_data"
  "${volume_prefix}_synapse_data"
  "${volume_prefix}_matrix_chat_appservice_runtime"
)

seed_owned_resources() {
  local resource_name
  mkdir -p "${state_dir}/container" "${state_dir}/network" "${state_dir}/volume"
  for resource_name in "${containers[@]}"; do
    : >"${state_dir}/container/${resource_name}"
  done
  : >"${state_dir}/network/${network_name}"
  for resource_name in "${volumes[@]}"; do
    : >"${state_dir}/volume/${resource_name}"
  done
  mkdir -p "$(dirname -- "${proof_token}")"
  openssl rand -hex 48 >"${proof_token}"
  chmod 600 "${proof_token}"
}

namespace_hash="$(printf '%s' "${namespace}" | shasum -a 256 | awk '{print $1}')"
jq -n --arg namespaceSha256 "${namespace_hash}" \
  '{schemaVersion:"weave.isolated-e2e-identities.v1",namespaceSha256:$namespaceSha256}' >"${identity_manifest}"
jq -n \
  --arg namespace "${namespace}" \
  --arg runId "${run_id}" \
  --arg candidateCommit "${candidate_commit}" \
  --arg candidateEvidenceRef "${candidate_evidence_ref}" \
  '{schemaVersion:"weave.isolated-e2e-teardown-ownership.v1",scope:"isolated",namespace:$namespace,runId:$runId,candidateCommit:$candidateCommit,candidateEvidenceRef:$candidateEvidenceRef,resourcePrefix:$namespace}' \
  >"${ownership_file}"
chmod 600 "${ownership_file}"

run_teardown() {
  env -i \
    PATH="${mock_bin}:${PATH}" \
    HOME="${test_root}" \
    MOCK_DOCKER_STATE="${state_dir}" \
    MOCK_DOCKER_NETWORK="${network_name}" \
    MOCK_NAMESPACE="${namespace}" \
    MOCK_PRESERVE_VOLUME="${MOCK_PRESERVE_VOLUME:-}" \
    WEAVE_IAC_BIN=tofu \
    WEAVE_E2E_STACK_SCOPE="${WEAVE_E2E_STACK_SCOPE:-isolated}" \
    WEAVE_E2E_RUN_ID="${run_id}" \
    WEAVE_E2E_OUTPUT_ROOT="${output_root}" \
    WEAVE_E2E_IDENTITY_MANIFEST_PATH="${identity_manifest}" \
    WEAVE_TEARDOWN_OWNERSHIP_FILE="${ownership_file}" \
    WEAVE_TEARDOWN_EVIDENCE_FILE="${evidence_file}" \
    WEAVE_CANDIDATE_COMMIT="${candidate_commit}" \
    WEAVE_CANDIDATE_EVIDENCE_REF="${candidate_evidence_ref}" \
    WEAVE_REMOVE_VOLUMES=true \
    WEAVE_CONFIRM_DESTRUCTIVE_RESET="${namespace}" \
    TF_VAR_tenant_slug="${namespace}" \
    TF_VAR_isolated_e2e_enabled=true \
    TF_VAR_isolated_e2e_namespace="${namespace}" \
    TF_VAR_docker_network_name="${network_name}" \
    TF_VAR_chat_e2e_proof_enabled=true \
    TF_VAR_chat_e2e_proof_token_host_path="${proof_token}" \
    TF_VAR_chat_e2e_proof_run_id="${run_id}" \
    bash "${TEARDOWN_SCRIPT}"
}

seed_owned_resources
run_teardown >/dev/null

jq -e \
  --arg candidate "${candidate_commit}" \
  --arg namespaceSha256 "${namespace_hash}" \
  '
    .schema == "weave.isolated-stack-teardown.v1" and
    .candidateCommit == $candidate and
    .namespaceSha256 == $namespaceSha256 and
    .isolatedRuntimeVerified == true and
    .providerNamespaceDestroyed == true and
    .chatProofCredentialDestroyed == true and
    .persistentDogfoodTouched == false and
    .credentialsIncluded == false and
    .rawProviderPayloadIncluded == false and
    .supportSafe == true and
    .postRemovalCounts.remainingOwnedResources == 0 and
    ([.postRemovalCounts.containers[]] | all(. == 0)) and
    ([.postRemovalCounts.networks[]] | all(. == 0)) and
    ([.postRemovalCounts.volumes[]] | all(. == 0)) and
    (.postRemovalCounts.containers | length) == 9 and
    (.postRemovalCounts.networks | length) == 1 and
    (.postRemovalCounts.volumes | length) == 8
  ' "${evidence_file}" >/dev/null
[[ ! -e "${proof_token}" ]] || { echo "teardown left the run-scoped Chat proof credential" >&2; exit 1; }

if grep -Eiq 'password|token|credential|provider(payload|body)' "${evidence_file}"; then
  # Boolean schema field names are allowed; raw values are not. Match only
  # assignment-like content after the structural assertion above.
  grep -Eiq '"(password|token|credential)"[[:space:]]*:' "${evidence_file}" && {
    echo "teardown evidence included a credential-shaped field" >&2
    exit 1
  }
fi

rm -f "${evidence_file}"
seed_owned_resources
if WEAVE_E2E_STACK_SCOPE=persistent run_teardown >/dev/null 2>&1; then
  echo "teardown evidence accepted a persistent runtime scope" >&2
  exit 1
fi
[[ ! -e "${evidence_file}" ]] || { echo "scope failure wrote teardown evidence" >&2; exit 1; }

rm -rf "${state_dir:?}"/*
mkdir -p "${state_dir}/container" "${state_dir}/network" "${state_dir}/volume"
: >"${state_dir}/volume/${volume_prefix}_matrix_chat_appservice_runtime"
if MOCK_PRESERVE_VOLUME="${volume_prefix}_matrix_chat_appservice_runtime" run_teardown >/dev/null 2>&1; then
  echo "teardown evidence accepted a surviving owned provider volume" >&2
  exit 1
fi
jq -e --arg survivingVolume "${volume_prefix}_matrix_chat_appservice_runtime" '
  .providerNamespaceDestroyed == false and
  .postRemovalCounts.remainingOwnedResources == 1 and
  .postRemovalCounts.volumes[$survivingVolume] == 1
' "${evidence_file}" >/dev/null

printf 'isolated teardown evidence tests passed\n'
