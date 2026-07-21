#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
readonly SCRIPT="${ROOT_DIR}/support-bundle.sh"

output_dir="$(mktemp -d)"
metrics_output_dir="$(mktemp -d)"
work_home="$(mktemp -d)"
bootstrap_env="${ROOT_DIR}/.generated/bootstrap.env"
app_config_env="${ROOT_DIR}/.generated/app-config.env"
operator_leak_fixture="${ROOT_DIR}/.generated/operator-leak-fixture.log"
provider_health_fixture="${ROOT_DIR}/.generated/provider-health-fixture.json"
provider_health_metrics_fixture="${ROOT_DIR}/.generated/provider-health-metrics-fixture.json"
bootstrap_backup=""
app_config_backup=""
operator_fixture_backup=""
provider_health_backup=""
provider_health_metrics_backup=""

backup_file() {
  local source="$1"
  if [[ -f "${source}" ]]; then
    local backup
    backup="$(mktemp)"
    cp "${source}" "${backup}"
    printf '%s\n' "${backup}"
  fi
}

bootstrap_backup="$(backup_file "${bootstrap_env}")"
app_config_backup="$(backup_file "${app_config_env}")"
operator_fixture_backup="$(backup_file "${operator_leak_fixture}")"
provider_health_backup="$(backup_file "${provider_health_fixture}")"
provider_health_metrics_backup="$(backup_file "${provider_health_metrics_fixture}")"

restore_file() {
  local backup="$1"
  local target="$2"
  if [[ -n "${backup}" && -f "${backup}" ]]; then
    mkdir -p "$(dirname -- "${target}")"
    cp "${backup}" "${target}"
    rm -f "${backup}"
  else
    rm -f "${target}"
  fi
}

# shellcheck disable=SC2329
cleanup() {
  restore_file "${bootstrap_backup}" "${bootstrap_env}"
  restore_file "${app_config_backup}" "${app_config_env}"
  restore_file "${operator_fixture_backup}" "${operator_leak_fixture}"
  restore_file "${provider_health_backup}" "${provider_health_fixture}"
  restore_file "${provider_health_metrics_backup}" "${provider_health_metrics_fixture}"
  rm -rf "${output_dir}" "${metrics_output_dir}" "${work_home}"
}
trap cleanup EXIT

mkdir -p "${ROOT_DIR}/.generated"
cat >"${operator_leak_fixture}" <<'LOG'
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.payloadpayload.signature123
cloud_access_key=AKIAABCDEFGHIJKLMNOP
callback=https://weave-admin:super-secret-password@provider.internal.example/path
-----BEGIN PRIVATE KEY-----
raw-private-key-material
-----END PRIVATE KEY-----
operator=person@example.com
weaver_secret_ref=secretref://weave/provider/openproject/user-refresh-token
weaver_credential_ref=credentialref://weave/runtime/cell/private
profile_signing_key_ref=rpk_0123456789abcdefghijklmnopqrstuv
state_wrapping_key_ref=rsk_0123456789abcdefghijklmnopqrstuv
LOG
cat >"${bootstrap_env}" <<'ENV'
TF_VAR_tenant_domain=weave.test
TF_VAR_public_scheme=https
TF_VAR_agent_runtime_enabled=true
TF_VAR_keycloak_admin_password=super-secret-admin
TF_VAR_nextcloud_backend_actor_token=super-secret-token
TF_VAR_interop_slack_signing_secret=slack-signing-secret
TF_VAR_boards_provider_api_token=boards-provider-secret
TF_VAR_boards_openproject_api_token=openproject-super-secret
TF_VAR_openproject_secret_key_base=openproject-secret-key-base
TF_VAR_matrix_chat_appservice_as_token=matrix-as-token-super-secret
TF_VAR_matrix_chat_appservice_hs_token=matrix-hs-token-super-secret
WEAVE_CHAT_E2E_PROOF_TOKEN=chat-proof-token-super-secret
WEAVE_API_BASE_URL=https://api.weave.test/api
WEAVE_OIDC_ISSUER_URL=https://auth.weave.test/realms/weave
WEAVE_MATRIX_PROVIDER_URL=https://matrix.weave.test
WEAVE_CHAT_PROVIDER=matrix-synapse
WEAVE_CHAT_STORAGE_MODE=jdbc
WEAVE_CHAT_MATRIX_APPSERVICE_CONFIGURED=true
ENV
cat >"${app_config_env}" <<'ENV'
WEAVE_PUBLIC_BASE_URL=https://weave.test
WEAVE_NEXTCLOUD_BASE_URL=https://files.weave.test
WEAVE_CALDAV_BACKEND_TOKEN=calendar-token
WEAVE_INTEROP_SLACK_TOKEN_REF=slack-token-ref
WEAVE_INTEROP_SLACK_CLIENT_SECRET_REF=slack-client-secret-ref
WEAVE_BOARDS_OPENPROJECT_BASE_URL=https://openproject.example
WEAVE_BOARDS_OPENPROJECT_API_TOKEN=openproject-app-token
WEAVE_BOARDS_PROVIDER_TOKEN=boards-runtime-token
WEAVE_WEAVER_RUNTIME_TOKEN=weaver-short-lived-token
WEAVE_WEAVER_CREDENTIAL_REF=secretref://weave/weaver/runtime/user-secret
ENV
cat >"${provider_health_fixture}" <<'JSON'
{
  "schemaVersion": "provider-capability-health-v1",
  "generatedAt": "2026-07-12T10:00:00Z",
  "supportSafe": true,
  "capabilities": [
    {
      "capability": "chat",
      "state": "available",
      "supportSafeCode": "provider-available",
      "correlationRef": "corr-chat-fixture",
      "observedAt": "2026-07-12T09:59:45Z",
      "nextProbeAt": "2026-07-12T10:01:45Z",
      "backoffUntil": null,
      "cachedAgeSeconds": 15,
      "stale": false,
      "consecutiveFailures": 0,
      "probeLatencyMillis": 40,
      "readinessTransitions": 0
    },
    {
      "capability": "files",
      "state": "degraded",
      "supportSafeCode": "provider-throttled",
      "correlationRef": "corr-files-fixture",
      "observedAt": "2026-07-12T09:59:00Z",
      "nextProbeAt": "2026-07-12T10:01:00Z",
      "backoffUntil": "2026-07-12T10:02:00Z",
      "cachedAgeSeconds": 60,
      "stale": false,
      "consecutiveFailures": 2,
      "probeLatencyMillis": 125,
      "readinessTransitions": 1
    },
    {
      "capability": "calendar",
      "state": "available",
      "supportSafeCode": "provider-available",
      "correlationRef": "corr-calendar-fixture",
      "observedAt": "2026-07-12T09:59:30Z",
      "nextProbeAt": "2026-07-12T10:01:30Z",
      "backoffUntil": null,
      "cachedAgeSeconds": 30,
      "stale": false,
      "consecutiveFailures": 0,
      "probeLatencyMillis": 85,
      "readinessTransitions": 0
    }
  ]
}
JSON
cat >"${provider_health_metrics_fixture}" <<'JSON'
{
  "schemaVersion": "weave.provider-health-metrics-summary.v1",
  "supportSafe": true,
  "source": "loopback-actuator-cached-metrics",
  "providerProbeTriggered": false,
  "overall": "degraded",
  "observedAtUtc": "2026-07-12T09:59:00Z",
  "cachedResultAgeSeconds": 60,
  "capabilities": {
    "chat": "available",
    "files": "degraded",
    "calendar": "available"
  },
  "details": {
    "files": {
      "cachedResultAgeSeconds": 60,
      "consecutiveFailures": 2,
      "backoffUntilEpochSeconds": 1783850520,
      "readinessTransitions": 1
    },
    "calendar": {
      "cachedResultAgeSeconds": 30,
      "consecutiveFailures": 0,
      "backoffUntilEpochSeconds": 0,
      "readinessTransitions": 0
    }
  },
  "rawMetricPayloadIncluded": false
}
JSON

(
  cd "${ROOT_DIR}"
  env -i \
    PATH="${PATH}" \
    HOME="${work_home}" \
    WEAVE_SUPPORT_BUNDLE_LOG_LINES=1 \
    WEAVE_SUPPORT_BUNDLE_RUN_CHECKS=false \
    WEAVE_PROVIDER_HEALTH_EVIDENCE_FILE="${provider_health_fixture}" \
    bash "${SCRIPT}" "${output_dir}"
)

archive="$(find "${output_dir}" -name 'weave-support-*.tar.gz' -print -quit)"
[[ -n "${archive}" ]] || { echo "support bundle archive was not created" >&2; exit 1; }

tar -xzf "${archive}" -C "${output_dir}"
extracted="$(find "${output_dir}" -maxdepth 1 -type d -name 'weave-support-*' -print -quit)"
[[ -n "${extracted}" ]] || { echo "support bundle archive did not extract" >&2; exit 1; }

grep -Fq 'This bundle is for support-safe diagnostics only. It is not a backup' "${extracted}/README.txt"
grep -Fq 'TF_VAR_tenant_domain=weave.test' "${extracted}/config/public-env-summary.env"
grep -Fq 'WEAVE_API_BASE_URL=https://api.weave.test/api' "${extracted}/config/public-env-summary.env"
grep -Fq 'WEAVE_NEXTCLOUD_BASE_URL_CONFIGURED=true' "${extracted}/config/public-env-summary.env"
grep -Fq 'WEAVE_BOARDS_OPENPROJECT_BASE_URL_CONFIGURED=true' "${extracted}/config/public-env-summary.env"
grep -Fq 'WEAVE_CHAT_PROVIDER=matrix-synapse' "${extracted}/config/public-env-summary.env"
grep -Fq 'WEAVE_CHAT_STORAGE_MODE=jdbc' "${extracted}/config/public-env-summary.env"
grep -Fq 'WEAVE_CHAT_MATRIX_APPSERVICE_CONFIGURED=true' "${extracted}/config/public-env-summary.env"
grep -Fq '"schema": "weave-support-safe-adapter-readiness-v1"' "${extracted}/checks/adapter-readiness-summary.json"
grep -Fq '"domain": "boards-tasks"' "${extracted}/checks/adapter-readiness-summary.json"
grep -Fq '"schemaVersion": "weave-support-agent-runtime-control-v1"' "${extracted}/checks/agent-runtime-control.json"
grep -Fq '"claimMaturity": "Guarded"' "${extracted}/checks/agent-runtime-control.json"
grep -Fq '"privateKeyMaterialIncluded": false' "${extracted}/checks/agent-runtime-control.json"
grep -Fq '"keyOrCredentialReferencesIncluded": false' "${extracted}/checks/agent-runtime-control.json"
grep -A6 -F '"domain": "chat"' "${extracted}/checks/adapter-readiness-summary.json" | grep -Fq '"configured": true'
grep -Fq '"adapterKey": "openproject-primary"' "${extracted}/checks/adapter-readiness-summary.json"
grep -Fq '"configured": true' "${extracted}/checks/adapter-readiness-summary.json"
grep -Fq '"artifactKind": "weave-support-bundle-redaction-report-v1"' "${extracted}/checks/support-redaction-report.json"
grep -Fq '"name": "negative_fixture_detects_unsafe_content"' "${extracted}/checks/support-redaction-report.json"
grep -Fq '"name": "chat_e2e_proof_token_and_run_binding"' "${extracted}/checks/support-redaction-report.json"
grep -Fq '"name": "agent_runtime_key_and_credential_refs"' "${extracted}/checks/support-redaction-report.json"
grep -Fq '"unsafeContentDetected": false' "${extracted}/checks/support-redaction-report.json"
grep -Fq '"schemaVersion": "weave-support-provider-capability-health-evidence-v1"' "${extracted}/checks/provider-capability-health.json"
grep -Fq '"collectionStatus": "collected"' "${extracted}/checks/provider-capability-health.json"
grep -Fq '"capability": "chat"' "${extracted}/checks/provider-capability-health.json"
grep -Fq '"capability": "files"' "${extracted}/checks/provider-capability-health.json"
grep -Fq '"cachedAgeSeconds": 60' "${extracted}/checks/provider-capability-health.json"
grep -Fq '"readinessTransitions": 1' "${extracted}/checks/provider-capability-health.json"
grep -Fq '"rawContentsIncluded": false' "${extracted}/recent-artifacts/summary.json"
grep -Fq 'Raw service/provider logs are excluded' "${extracted}/logs/README.txt"
if find "${extracted}/logs" -type f ! -name README.txt -print -quit | grep -q .; then
  echo "support bundle included raw service/provider logs" >&2
  exit 1
fi

(
  cd "${ROOT_DIR}"
  env -i \
    PATH="${PATH}" \
    HOME="${work_home}" \
    WEAVE_SUPPORT_BUNDLE_LOG_LINES=1 \
    WEAVE_SUPPORT_BUNDLE_RUN_CHECKS=false \
    WEAVE_PROVIDER_HEALTH_EVIDENCE_FILE="${provider_health_metrics_fixture}" \
    bash "${SCRIPT}" "${metrics_output_dir}"
)

metrics_archive="$(find "${metrics_output_dir}" -name 'weave-support-*.tar.gz' -print -quit)"
[[ -n "${metrics_archive}" ]] || { echo "metrics support bundle archive was not created" >&2; exit 1; }
tar -xzf "${metrics_archive}" -C "${metrics_output_dir}"
metrics_extracted="$(find "${metrics_output_dir}" -maxdepth 1 -type d -name 'weave-support-*' -print -quit)"
[[ -n "${metrics_extracted}" ]] || { echo "metrics support bundle archive did not extract" >&2; exit 1; }
metrics_evidence="${metrics_extracted}/checks/provider-capability-health.json"
grep -Fq '"schemaVersion": "weave-support-provider-health-metrics-summary-evidence-v1"' "${metrics_evidence}"
grep -Fq '"sourceSchema": "weave.provider-health-metrics-summary.v1"' "${metrics_evidence}"
grep -Fq '"source": "loopback-actuator-cached-metrics"' "${metrics_evidence}"
grep -Fq '"providerProbeTriggered": false' "${metrics_evidence}"
grep -Fq '"rawMetricPayloadIncluded": false' "${metrics_evidence}"
grep -Fq '"overall": "degraded"' "${metrics_evidence}"
grep -Fq '"cachedResultAgeSeconds": 60' "${metrics_evidence}"
grep -Fq '"readinessTransitions": 1' "${metrics_evidence}"

if grep -R -Fq 'super-secret' "${extracted}" || grep -R -Fq 'matrix-as-token' "${extracted}" || grep -R -Fq 'matrix-hs-token' "${extracted}" || grep -R -Fq 'chat-proof-token' "${extracted}" || grep -R -Fq 'calendar-token' "${extracted}" || grep -R -Fq 'slack-signing-secret' "${extracted}" || grep -R -Fq 'slack-client-secret-ref' "${extracted}" || grep -R -Fq 'openproject-super-secret' "${extracted}" || grep -R -Fq 'openproject-secret-key-base' "${extracted}" || grep -R -Fq 'openproject-app-token' "${extracted}" || grep -R -Fq 'boards-provider-secret' "${extracted}" || grep -R -Fq 'boards-runtime-token' "${extracted}" || grep -R -Fq 'weaver-short-lived-token' "${extracted}" || grep -R -Fq 'secretref://weave' "${extracted}" || grep -R -Fq 'credentialref://weave' "${extracted}" || grep -R -Fq 'rpk_0123456789' "${extracted}" || grep -R -Fq 'rsk_0123456789' "${extracted}" || grep -R -Fq 'openproject.example' "${extracted}" || grep -R -Fq 'files.weave.test' "${extracted}" || grep -R -Fq 'AKIAABCDEFGHIJKLMNOP' "${extracted}" || grep -R -Fq 'person@example.com' "${extracted}" || grep -R -Fq 'raw-private-key-material' "${extracted}"; then
  echo "support bundle leaked a test secret" >&2
  grep -R -n -E 'super-secret|matrix-as-token|matrix-hs-token|chat-proof-token|calendar-token|slack-signing-secret|slack-client-secret-ref|openproject-super-secret|openproject-secret-key-base|openproject-app-token|boards-provider-secret|boards-runtime-token|weaver-short-lived-token|secretref://weave|openproject\.example|files\.weave\.test|AKIAABCDEFGHIJKLMNOP|person@example\.com|raw-private-key-material' "${extracted}" >&2 || true
  exit 1
fi

# Scanner failures must be actionable without echoing the secret value back into CI logs.
# shellcheck source=infra/weave-workspace/support-bundle.sh
source "${SCRIPT}"
leaky_dir="$(mktemp -d)"
printf 'api_password=hunter2\n' >"${leaky_dir}/leak.txt"
if scan_for_unredacted_secrets "${leaky_dir}" 2>"${leaky_dir}/scan.err"; then
  echo "support bundle scanner accepted a known leak fixture" >&2
  exit 1
fi
if grep -Fq 'hunter2' "${leaky_dir}/scan.err"; then
  echo "support bundle scanner printed a raw secret value" >&2
  cat "${leaky_dir}/scan.err" >&2
  exit 1
fi
rm -rf "${leaky_dir}"

unsafe_provider_fixture="$(mktemp)"
unsafe_provider_output="$(mktemp)"
jq '.capabilities[0].rawProviderResponse = "credential-bearing provider body"' \
  "${provider_health_fixture}" >"${unsafe_provider_fixture}"
if sanitize_provider_capability_health "${unsafe_provider_fixture}" "${unsafe_provider_output}" 2>/dev/null; then
  echo "provider health sanitizer accepted an unknown raw-provider field" >&2
  exit 1
fi
rm -f "${unsafe_provider_fixture}" "${unsafe_provider_output}"

unsafe_metrics_fixture="$(mktemp)"
unsafe_metrics_output="$(mktemp)"
jq '.rawMetricResponse = {"tag": "capability", "value": "credential-bearing payload"}' \
  "${provider_health_metrics_fixture}" >"${unsafe_metrics_fixture}"
if sanitize_provider_health_metrics_summary "${unsafe_metrics_fixture}" "${unsafe_metrics_output}" 2>/dev/null; then
  echo "provider metrics sanitizer accepted an unknown raw-metric field" >&2
  exit 1
fi
if sanitize_provider_capability_health "${provider_health_metrics_fixture}" "${unsafe_metrics_output}" 2>/dev/null; then
  echo "capability-health sanitizer accepted the metrics-summary schema" >&2
  exit 1
fi
if sanitize_provider_health_metrics_summary "${provider_health_fixture}" "${unsafe_metrics_output}" 2>/dev/null; then
  echo "metrics-summary sanitizer accepted the capability-health schema" >&2
  exit 1
fi
rm -f "${unsafe_metrics_fixture}" "${unsafe_metrics_output}"

grep -Fq '/v1/admin/provider-capability-health' "${SCRIPT}"
if grep -Fq '/api/health/providers' "${SCRIPT}"; then
  echo "support bundle must not use an unauthenticated provider-health route" >&2
  exit 1
fi

if grep -R -Eq 'PASSWORD=|TOKEN=|SECRET=' "${extracted}/config/public-env-summary.env"; then
  echo "support bundle public env summary included secret keys" >&2
  cat "${extracted}/config/public-env-summary.env" >&2
  exit 1
fi

printf 'support bundle redaction tests passed\n'
