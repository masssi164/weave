#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
readonly ROOT_DIR
readonly SCRIPT="${ROOT_DIR}/live-stack-failure-diagnostics.sh"

work_dir="$(mktemp -d)"
trap 'rm -rf -- "${work_dir}"' EXIT

stub_bin="${work_dir}/bin"
mkdir -p "${stub_bin}"
cat >"${stub_bin}/docker" <<'DOCKER'
#!/usr/bin/env bash
set -euo pipefail
case "${1:-}" in
  inspect)
    name="${@: -1}"
    printf '/%s\trunning\tnone\t0\n' "${name}"
    ;;
  logs)
    cat <<'LOGS'
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.payloadpayload.signature123
callback=https://runner:secret-password@provider.internal.example/path
operator=person@example.com
LOGS
    ;;
  ps)
    printf 'weave-backend\trunning\n'
    ;;
  volume)
    printf 'local weave-data\n'
    ;;
  system)
    printf 'TYPE TOTAL ACTIVE SIZE RECLAIMABLE\n'
    ;;
  exec)
    printf 'stubbed-docker-exec\n'
    ;;
  *)
    printf 'stubbed docker %s\n' "$*"
    ;;
esac
DOCKER
chmod +x "${stub_bin}/docker"
cat >"${stub_bin}/curl" <<'CURL'
#!/usr/bin/env bash
set -euo pipefail
if printf '%s\n' "$@" | grep -qx -- '-w'; then
  printf '503'
else
  printf '{"status":"down"}'
fi
CURL
chmod +x "${stub_bin}/curl"

acceptance_dir="${work_dir}/acceptance"
output_dir="${acceptance_dir}/failure-diagnostics"
mkdir -p "${acceptance_dir}"
cat >"${acceptance_dir}/scenario-mapping-results.json" <<'JSON'
{
  "scenarioResults": [
    {
      "runtimeStatus": "failedOrIncomplete",
      "scenario": {"name": "Matrix chat sends and reads a workspace message"},
      "markers": [{"marker": "CHAT_RESULT"}]
    }
  ]
}
JSON

PATH="${stub_bin}:${PATH}" \
  WEAVE_ACCEPTANCE_EVIDENCE_DIR="${acceptance_dir}" \
  WEAVE_LIVE_STACK_PRIVATE_RAW_LOGS=false \
  bash "${SCRIPT}" "${output_dir}"

[[ -s "${output_dir}/failure-summary.md" ]] || { echo "missing failure summary markdown" >&2; exit 1; }
[[ -s "${output_dir}/failure-summary.json" ]] || { echo "missing failure summary json" >&2; exit 1; }
[[ -s "${output_dir}/container-status.tsv" ]] || { echo "missing container status" >&2; exit 1; }
grep -Fq 'intentionally does not dump raw container logs' "${output_dir}/failure-summary.md"
grep -Fq 'rawContainerLogsIncluded": false' "${output_dir}/failure-summary.json"
grep -Fq 'CHAT_RESULT' "${output_dir}/failed-markers.json"

if grep -R -Fq 'secret-password' "${output_dir}" || grep -R -Fq 'person@example.com' "${output_dir}" || grep -R -Fq 'eyJhbGci' "${output_dir}"; then
  echo "failure diagnostics leaked raw private log content" >&2
  grep -R -n -E 'secret-password|person@example\.com|eyJhbGci' "${output_dir}" >&2 || true
  exit 1
fi

printf '%s\n' 'Live Stack failure diagnostics fixture test passed'
