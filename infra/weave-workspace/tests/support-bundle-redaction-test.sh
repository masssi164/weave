#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
readonly SCRIPT="${ROOT_DIR}/support-bundle.sh"

output_dir="$(mktemp -d)"
work_home="$(mktemp -d)"
bootstrap_env="${ROOT_DIR}/.generated/bootstrap.env"
app_config_env="${ROOT_DIR}/.generated/app-config.env"
operator_leak_fixture="${ROOT_DIR}/.generated/operator-leak-fixture.log"
bootstrap_backup=""
app_config_backup=""

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

cleanup() {
  restore_file "${bootstrap_backup}" "${bootstrap_env}"
  restore_file "${app_config_backup}" "${app_config_env}"
  rm -f "${operator_leak_fixture}"
  rm -rf "${output_dir}" "${work_home}"
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
LOG
cat >"${bootstrap_env}" <<'ENV'
TF_VAR_tenant_domain=weave.local
TF_VAR_public_scheme=https
TF_VAR_keycloak_admin_password=super-secret-admin
TF_VAR_nextcloud_backend_actor_token=super-secret-token
TF_VAR_interop_slack_signing_secret=slack-signing-secret
TF_VAR_boards_provider_api_token=boards-provider-secret
TF_VAR_boards_openproject_api_token=openproject-super-secret
TF_VAR_openproject_secret_key_base=openproject-secret-key-base
WEAVE_API_BASE_URL=https://api.weave.local/api
WEAVE_OIDC_ISSUER_URL=https://auth.weave.local/realms/weave
ENV
cat >"${app_config_env}" <<'ENV'
WEAVE_PUBLIC_BASE_URL=https://weave.local
WEAVE_NEXTCLOUD_BASE_URL=https://files.weave.local
WEAVE_CALDAV_BACKEND_TOKEN=calendar-token
WEAVE_INTEROP_SLACK_TOKEN_REF=slack-token-ref
WEAVE_INTEROP_SLACK_CLIENT_SECRET_REF=slack-client-secret-ref
WEAVE_BOARDS_OPENPROJECT_BASE_URL=https://openproject.example
WEAVE_BOARDS_OPENPROJECT_API_TOKEN=openproject-app-token
WEAVE_BOARDS_PROVIDER_TOKEN=boards-runtime-token
ENV

(
  cd "${ROOT_DIR}"
  env -i \
    PATH="${PATH}" \
    HOME="${work_home}" \
    WEAVE_SUPPORT_BUNDLE_LOG_LINES=1 \
    WEAVE_SUPPORT_BUNDLE_RUN_CHECKS=false \
    bash "${SCRIPT}" "${output_dir}"
)

archive="$(find "${output_dir}" -name 'weave-support-*.tar.gz' -print -quit)"
[[ -n "${archive}" ]] || { echo "support bundle archive was not created" >&2; exit 1; }

tar -xzf "${archive}" -C "${output_dir}"
extracted="$(find "${output_dir}" -maxdepth 1 -type d -name 'weave-support-*' -print -quit)"
[[ -n "${extracted}" ]] || { echo "support bundle archive did not extract" >&2; exit 1; }

grep -Fq 'This bundle is for support-safe diagnostics only. It is not a backup' "${extracted}/README.txt"
grep -Fq 'TF_VAR_tenant_domain=weave.local' "${extracted}/config/public-env-summary.env"
grep -Fq 'WEAVE_API_BASE_URL=https://api.weave.local/api' "${extracted}/config/public-env-summary.env"
grep -Fq 'WEAVE_NEXTCLOUD_BASE_URL_CONFIGURED=true' "${extracted}/config/public-env-summary.env"
grep -Fq 'WEAVE_BOARDS_OPENPROJECT_BASE_URL_CONFIGURED=true' "${extracted}/config/public-env-summary.env"
grep -Fq '"schema": "weave-support-safe-adapter-readiness-v1"' "${extracted}/checks/adapter-readiness-summary.json"
grep -Fq '"domain": "boards-tasks"' "${extracted}/checks/adapter-readiness-summary.json"
grep -Fq '"adapterKey": "openproject-primary"' "${extracted}/checks/adapter-readiness-summary.json"
grep -Fq '"configured": true' "${extracted}/checks/adapter-readiness-summary.json"

if grep -R -Fq 'super-secret' "${extracted}" || grep -R -Fq 'calendar-token' "${extracted}" || grep -R -Fq 'slack-signing-secret' "${extracted}" || grep -R -Fq 'slack-client-secret-ref' "${extracted}" || grep -R -Fq 'openproject-super-secret' "${extracted}" || grep -R -Fq 'openproject-secret-key-base' "${extracted}" || grep -R -Fq 'openproject-app-token' "${extracted}" || grep -R -Fq 'boards-provider-secret' "${extracted}" || grep -R -Fq 'boards-runtime-token' "${extracted}" || grep -R -Fq 'openproject.example' "${extracted}" || grep -R -Fq 'files.weave.local' "${extracted}" || grep -R -Fq 'AKIAABCDEFGHIJKLMNOP' "${extracted}" || grep -R -Fq 'person@example.com' "${extracted}" || grep -R -Fq 'raw-private-key-material' "${extracted}"; then
  echo "support bundle leaked a test secret" >&2
  grep -R -n -E 'super-secret|calendar-token|slack-signing-secret|slack-client-secret-ref|openproject-super-secret|openproject-secret-key-base|openproject-app-token|boards-provider-secret|boards-runtime-token|openproject\.example|files\.weave\.local|AKIAABCDEFGHIJKLMNOP|person@example\.com|raw-private-key-material' "${extracted}" >&2 || true
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

if grep -R -Eq 'PASSWORD=|TOKEN=|SECRET=' "${extracted}/config/public-env-summary.env"; then
  echo "support bundle public env summary included secret keys" >&2
  cat "${extracted}/config/public-env-summary.env" >&2
  exit 1
fi

printf 'support bundle redaction tests passed\n'
