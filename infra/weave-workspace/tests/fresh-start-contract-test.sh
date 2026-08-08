#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT="${ROOT_DIR}/fresh-start.sh"
PYTHON_SCRIPT="${ROOT_DIR}/fresh-start.py"
TMP_DIR="$(mktemp -d)"
MOCK_BIN="${TMP_DIR}/bin"
ALLOWLIST="${TMP_DIR}/targets.json"
PLAN="${TMP_DIR}/plan.json"
APPLY_EVIDENCE="${TMP_DIR}/apply-evidence.json"
APPROVAL_EVIDENCE="${TMP_DIR}/approval-evidence.json"
REMOVALS="${TMP_DIR}/removals.log"
MOCK_STATE="${TMP_DIR}/mock-state"
SPEC_COMMIT="1111111111111111111111111111111111111111"
SPEC_DIGEST="sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
CANDIDATE_COMMIT="2222222222222222222222222222222222222222"
CANDIDATE_DIGEST="sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
OPERATION_NONCE="fresh-start-op-0001"
trap 'rm -rf "${TMP_DIR}"' EXIT

for label in \
  environment scope stack generation namespace component data-class fresh-start-eligible \
  spec-commit spec-digest candidate-commit candidate-manifest-digest; do
  grep -Fq "com.massimotter.weave.${label}" "${ROOT_DIR}/compose.yaml" ||
    { echo "missing creation-time ownership label ${label}" >&2; exit 1; }
  grep -Fq "\"com.massimotter.weave.${label}\"" "${ROOT_DIR}/scripts/compose_runtime.py" ||
    { echo "missing external-resource ownership label ${label}" >&2; exit 1; }
done
grep -Fq 'org.opencontainers.image.revision' "${ROOT_DIR}/keycloak/Dockerfile.identity-ops"
grep -Fq 'profile = environment' "${ROOT_DIR}/scripts/compose_env.py"
grep -Fq 'PUBLISHED_DIGEST_IMAGE_RE' "${ROOT_DIR}/scripts/compose_env.py"
grep -Fq 'Fresh Start is forbidden for prod' "${PYTHON_SCRIPT}"
grep -Fq "DELETE_OLD_WEAVE:{digest}" "${PYTHON_SCRIPT}"
grep -Fq 'canonical_json_bytes' "${PYTHON_SCRIPT}"
grep -Fq '"WEAVE_KEYCLOAK_IMAGE": image_reference(' "${ROOT_DIR}/fresh-start-recreate.py"
grep -Fq 'candidate, "keycloak-runtime"' "${ROOT_DIR}/fresh-start-recreate.py"
grep -Fq '"name": "weave-dogfood-backend"' "${ROOT_DIR}/fresh-start-targets.json"
grep -Fq '"name": "weave_dogfood_native_files_data"' "${ROOT_DIR}/fresh-start-targets.json"
if grep -Eq 'docker (system|container|volume|network) prune|name.*startswith|prefix|glob' "${PYTHON_SCRIPT}"; then
  echo "Fresh Start contains a broad selection/deletion primitive" >&2
  exit 1
fi

mkdir -p "${MOCK_BIN}"
mkdir -p "${MOCK_STATE}"
cat >"${ALLOWLIST}" <<'JSON'
{
  "schemaVersion":"weave.infra.fresh-start-targets.v1",
  "environment":"persistent-dogfood",
  "stack":"weave",
  "targets":[
    {"kind":"container","name":"weave-backend","component":"server","dataClass":"runtime-ephemeral"},
    {"kind":"volume","name":"weave-db-data-fixture","component":"postgres","dataClass":"database-sensitive"}
  ],
  "exclusions":[]
}
JSON

cat >"${MOCK_BIN}/docker" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
kind="${1:-}"
operation="${2:-}"
name="${3:-}"
if [[ "${operation}" == ls ]]; then
  [[ "${MOCK_LABEL_MODE:-legacy}" == current || "${MOCK_LABEL_MODE:-legacy}" == partial ]] || exit 0
  case "${kind}" in
    container)
      [[ -f "${MOCK_STATE}/container-weave-backend" ]] ||
        printf '%s\n' weave-backend
      ;;
    volume)
      [[ -f "${MOCK_STATE}/volume-weave-db-data-fixture" ]] ||
        printf '%s\n' weave-db-data-fixture
      ;;
    network)
      ;;
  esac
  exit 0
fi
if [[ "${operation}" == inspect ]]; then
  [[ ! -f "${MOCK_STATE}/${kind}-${name}" ]] || exit 1
  labels='{}'
  case "${MOCK_LABEL_MODE:-legacy}" in
    current)
      component=server
      data_class=runtime-ephemeral
      [[ "${kind}" != volume ]] || { component=postgres; data_class=database-sensitive; }
      labels="$(jq -cn \
        --arg component "${component}" \
        --arg dataClass "${data_class}" \
        '{
          "com.massimotter.weave.managed":"true",
          "com.massimotter.weave.environment":"persistent-dogfood",
          "com.massimotter.weave.scope":"persistent",
          "com.massimotter.weave.stack":"weave",
          "com.massimotter.weave.generation":"fresh-v1",
          "com.massimotter.weave.namespace":"weave",
          "com.massimotter.weave.component":$component,
          "com.massimotter.weave.data-class":$dataClass,
          "com.massimotter.weave.fresh-start-eligible":"true",
          "com.massimotter.weave.spec-commit":"1111111111111111111111111111111111111111",
          "com.massimotter.weave.spec-digest":"sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
          "com.massimotter.weave.candidate-commit":"2222222222222222222222222222222222222222",
          "com.massimotter.weave.candidate-manifest-digest":"sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        }')"
      ;;
    partial)
      labels='{"com.massimotter.weave.managed":"true"}'
      ;;
    metadata-only)
      labels="$(jq -cn --arg version "${MOCK_METADATA_VERSION:-26.7.0}" \
        '{"com.massimotter.weave.keycloak.version":$version}')"
      ;;
  esac
  suffix=""
  [[ "${MOCK_CHANGED_ID:-false}" != true ]] || suffix="-changed"
  if [[ "${kind}" == container ]]; then
    jq -cn --arg id "container-id-${name}${suffix}" --argjson labels "${labels}" \
      --arg name "/${name}" \
      '[{Id:$id,Name:$name,Image:"sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",Config:{Labels:$labels}}]'
  elif [[ "${kind}" == volume ]]; then
    jq -cn --arg name "${name}${suffix}" --argjson labels "${labels}" '[{Name:$name,Labels:$labels}]'
  else
    exit 1
  fi
  exit 0
fi
if [[ "${operation}" == rm ]]; then
  printf '%s\n' "$*" >>"${MOCK_REMOVALS}"
  resource_id="${*: -1}"
  case "${kind}" in
    container)
      resource_name="${resource_id#container-id-}"
      resource_name="${resource_name%-changed}"
      ;;
    volume)
      resource_name="${resource_id%-changed}"
      ;;
    *)
      resource_name="${resource_id}"
      ;;
  esac
  : >"${MOCK_STATE}/${kind}-${resource_name}"
  exit 0
fi
exit 1
MOCK
chmod +x "${MOCK_BIN}/docker"

plan() {
  PATH="${MOCK_BIN}:${PATH}" MOCK_LABEL_MODE="${1:-legacy}" MOCK_STATE="${MOCK_STATE}" \
    "${SCRIPT}" plan \
      --environment persistent-dogfood \
      --scope persistent \
      --stack weave \
      --namespace weave \
      --retired-generation fresh-v1 \
      --target-generation fresh-v2 \
      --spec-commit "${SPEC_COMMIT}" \
      --spec-digest "${SPEC_DIGEST}" \
      --candidate-commit "${CANDIDATE_COMMIT}" \
      --candidate-manifest-digest "${CANDIDATE_DIGEST}" \
      --operation-nonce "${OPERATION_NONCE}" \
      --recovery-decision approved-no-recovery \
      --recovery-evidence-ref https://evidence.weave.test/fresh-start/no-recovery \
      --allowlist "${ALLOWLIST}" \
      --lock-file "${TMP_DIR}/fresh-start.lock" \
      --output "${PLAN}"
}

write_approval() {
  python3 - "${APPROVAL_EVIDENCE}" "$1" <<'PY'
import json
import sys

path, digest = sys.argv[1:]
payload = {
    "schemaVersion": "weave.infra.fresh-start-approval.v1",
    "supportSafe": True,
    "decision": "approved",
    "environment": "persistent-dogfood",
    "planSha256": digest,
    "operationNonce": "fresh-start-op-0001",
    "approverRole": "weave-platform-ops-lead",
    "evidenceRef": "https://evidence.weave.test/fresh-start/approval",
}
with open(path, "wb") as output:
    output.write(json.dumps(payload, ensure_ascii=False, separators=(",", ":"), sort_keys=True).encode())
PY
}

plan legacy >/dev/null
first_digest="$(shasum -a 256 "${PLAN}" | awk '{print $1}')"
cp "${PLAN}" "${TMP_DIR}/first-plan.json"
plan legacy >/dev/null
cmp "${TMP_DIR}/first-plan.json" "${PLAN}"
[[ "$(shasum -a 256 "${PLAN}" | awk '{print $1}')" == "${first_digest}" ]]
jq -e --arg specDigest "${SPEC_DIGEST}" '
  .supportSafe == true and
  .environment == "persistent-dogfood" and
  .retiredGeneration == "fresh-v1" and
  .targetGeneration == "fresh-v2" and
  .specDigest == $specDigest and
  .operationNonce == "fresh-start-op-0001" and
  .recoveryDecision.decision == "approved-no-recovery" and
  (.targets | length) == 2 and
  all(.targets[]; .ownershipClassification == "legacy-exact-allowlist")
' "${PLAN}" >/dev/null

write_approval "${first_digest}"

if PATH="${MOCK_BIN}:${PATH}" MOCK_REMOVALS="${REMOVALS}" \
  MOCK_STATE="${MOCK_STATE}" "${SCRIPT}" apply --manifest "${PLAN}" \
    --allowlist "${ALLOWLIST}" --approval-evidence "${APPROVAL_EVIDENCE}" \
    --lock-file "${TMP_DIR}/fresh-start.lock" \
    --confirm DELETE_OLD_WEAVE:wrong >/dev/null 2>&1; then
  echo "wrong confirmation was accepted" >&2
  exit 1
fi

if PATH="${MOCK_BIN}:${PATH}" MOCK_REMOVALS="${REMOVALS}" \
  MOCK_STATE="${MOCK_STATE}" "${SCRIPT}" apply --manifest "${PLAN}" \
    --allowlist "${ALLOWLIST}" --approval-evidence "${APPROVAL_EVIDENCE}" \
    --lock-file "${TMP_DIR}/fresh-start.lock" \
    --confirm "persistent-dogfood:DELETE_OLD_WEAVE:${first_digest}" >/dev/null 2>&1; then
  echo "obsolete environment-prefixed confirmation was accepted" >&2
  exit 1
fi

confirmation="DELETE_OLD_WEAVE:${first_digest}"
PATH="${MOCK_BIN}:${PATH}" MOCK_REMOVALS="${REMOVALS}" MOCK_STATE="${MOCK_STATE}" \
  "${SCRIPT}" apply --manifest "${PLAN}" --allowlist "${ALLOWLIST}" \
    --approval-evidence "${APPROVAL_EVIDENCE}" \
    --evidence "${APPLY_EVIDENCE}" --lock-file "${TMP_DIR}/fresh-start.lock" \
    --confirm "${confirmation}" >/dev/null
grep -Fxq 'container rm --force container-id-weave-backend' "${REMOVALS}"
grep -Fxq 'volume rm weave-db-data-fixture' "${REMOVALS}"
jq -e \
  --arg digest "${first_digest}" \
  '.supportSafe == true and
   .planSha256 == $digest and
   .status == "removed-pending-target-recreation" and
   .exclusionsVerified == true and
   (.results | length) == 2 and
   all(.results[]; .status == "removed")' \
  "${APPLY_EVIDENCE}" >/dev/null

rm -f "${MOCK_STATE}"/*
if PATH="${MOCK_BIN}:${PATH}" MOCK_CHANGED_ID=true MOCK_REMOVALS="${REMOVALS}" \
  MOCK_STATE="${MOCK_STATE}" "${SCRIPT}" apply --manifest "${PLAN}" \
  --allowlist "${ALLOWLIST}" --approval-evidence "${APPROVAL_EVIDENCE}" \
  --lock-file "${TMP_DIR}/fresh-start.lock" \
  --confirm "${confirmation}" >/dev/null 2>&1; then
  echo "changed target identity was accepted" >&2
  exit 1
fi

if plan partial >/dev/null 2>&1; then
  echo "partial ownership labels were accepted" >&2
  exit 1
fi

plan metadata-only >/dev/null
jq -e '
  all(.targets[];
    .ownershipClassification == "legacy-exact-allowlist" and
    .ownershipLabels == {"com.massimotter.weave.keycloak.version":"26.7.0"}
  )
' "${PLAN}" >/dev/null
metadata_digest="$(shasum -a 256 "${PLAN}" | awk '{print $1}')"
write_approval "${metadata_digest}"
if PATH="${MOCK_BIN}:${PATH}" MOCK_LABEL_MODE=metadata-only MOCK_METADATA_VERSION=26.7.1 \
  MOCK_REMOVALS="${REMOVALS}" MOCK_STATE="${MOCK_STATE}" \
  "${SCRIPT}" apply --manifest "${PLAN}" --allowlist "${ALLOWLIST}" \
    --approval-evidence "${APPROVAL_EVIDENCE}" \
    --lock-file "${TMP_DIR}/fresh-start.lock" \
    --confirm "DELETE_OLD_WEAVE:${metadata_digest}" >/dev/null 2>&1; then
  echo "changed informational metadata was accepted" >&2
  exit 1
fi
PATH="${MOCK_BIN}:${PATH}" MOCK_LABEL_MODE=metadata-only MOCK_REMOVALS="${REMOVALS}" \
  MOCK_STATE="${MOCK_STATE}" "${SCRIPT}" apply --manifest "${PLAN}" \
    --allowlist "${ALLOWLIST}" --approval-evidence "${APPROVAL_EVIDENCE}" \
    --lock-file "${TMP_DIR}/fresh-start.lock" \
    --confirm "DELETE_OLD_WEAVE:${metadata_digest}" >/dev/null
rm -f "${MOCK_STATE}"/*

plan current >/dev/null
jq -e 'all(.targets[]; .ownershipClassification == "current-exact-labels")' "${PLAN}" >/dev/null

if PATH="${MOCK_BIN}:${PATH}" MOCK_STATE="${MOCK_STATE}" "${SCRIPT}" plan \
  --environment prod --scope persistent --stack weave --namespace weave \
  --retired-generation fresh-v1 \
  --target-generation fresh-v2 \
  --spec-commit "${SPEC_COMMIT}" --spec-digest "${SPEC_DIGEST}" \
  --candidate-commit "${CANDIDATE_COMMIT}" \
  --candidate-manifest-digest "${CANDIDATE_DIGEST}" \
  --operation-nonce "${OPERATION_NONCE}" \
  --recovery-decision approved-no-recovery \
  --recovery-evidence-ref https://evidence.weave.test/fresh-start/no-recovery \
  --lock-file "${TMP_DIR}/fresh-start.lock" \
  --allowlist "${ALLOWLIST}" --output "${PLAN}" >/dev/null 2>&1; then
  echo "prod Fresh Start was accepted" >&2
  exit 1
fi

echo "fresh-start contract tests passed"
