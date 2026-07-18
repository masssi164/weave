#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT="${ROOT_DIR}/persistent-dogfood-observation.sh"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

fail() { printf '%s\n' "$*" >&2; exit 1; }

before="${TMP_DIR}/before.json"
after="${TMP_DIR}/after.json"
comparison="${TMP_DIR}/comparison.json"
cat >"${before}" <<'JSON'
{
  "schemaVersion":"weave.persistent-dogfood-observation.v1",
  "deploymentScope":"persistent-dogfood",
  "staticTestUserEnabled":false,
  "isolatedE2eEnabled":false,
  "humanMember":{"state":"active","subjectSha256":"subject-hash"},
  "mailpit":{"volumeIdentitySha256":"volume-hash","messageCount":1,"databaseBytes":4096,"databaseSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},
  "tls":{"caSha256":"ca-hash","leafSha256":"leaf-hash"},
  "activeSessions":{"count":1,"setSha256":"session-hash"},
  "supportSafe":true
}
JSON
cp "${before}" "${after}"

env \
  WEAVE_DOGFOOD_BOOTSTRAP_ENV="${TMP_DIR}/missing-bootstrap.env" \
  WEAVE_DOGFOOD_DEPLOYMENT_SCOPE=persistent-dogfood \
  TF_VAR_create_test_user=false \
  TF_VAR_isolated_e2e_enabled=false \
  TF_VAR_isolated_e2e_namespace= \
  TF_VAR_isolated_e2e_context_memberships='[]' \
  bash "${SCRIPT}" compare --before "${before}" --after "${after}" --output "${comparison}" >/dev/null
jq -e '.status == "passed" and .twoNonDestructiveInstallsPreservedState == true and all(.gates[]; .passed)' "${comparison}" >/dev/null

jq '.tls.leafSha256 = "changed-leaf"' "${before}" >"${after}"
if env \
  WEAVE_DOGFOOD_BOOTSTRAP_ENV="${TMP_DIR}/missing-bootstrap.env" \
  WEAVE_DOGFOOD_DEPLOYMENT_SCOPE=persistent-dogfood \
  TF_VAR_create_test_user=false \
  TF_VAR_isolated_e2e_enabled=false \
  TF_VAR_isolated_e2e_namespace= \
  TF_VAR_isolated_e2e_context_memberships='[]' \
  bash "${SCRIPT}" compare --before "${before}" --after "${after}" --output "${comparison}" >/dev/null 2>&1; then
  fail "comparison accepted a changed TLS identity"
fi
jq -e '.status == "failed" and any(.gates[]; .gate == "tls_leaf" and .passed == false)' "${comparison}" >/dev/null

jq '.mailpit.databaseSha256 = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"' "${before}" >"${after}"
if env \
  WEAVE_DOGFOOD_BOOTSTRAP_ENV="${TMP_DIR}/missing-bootstrap.env" \
  WEAVE_DOGFOOD_DEPLOYMENT_SCOPE=persistent-dogfood \
  TF_VAR_create_test_user=false \
  TF_VAR_isolated_e2e_enabled=false \
  TF_VAR_isolated_e2e_namespace= \
  TF_VAR_isolated_e2e_context_memberships='[]' \
  bash "${SCRIPT}" compare --before "${before}" --after "${after}" --output "${comparison}" >/dev/null 2>&1; then
  fail "comparison accepted changed Mailpit database content"
fi
jq -e '.status == "failed" and any(.gates[]; .gate == "mailpit_database_hash" and .passed == false)' "${comparison}" >/dev/null

jq '.mailpit.databaseBytes = 8192' "${before}" >"${after}"
if env \
  WEAVE_DOGFOOD_BOOTSTRAP_ENV="${TMP_DIR}/missing-bootstrap.env" \
  WEAVE_DOGFOOD_DEPLOYMENT_SCOPE=persistent-dogfood \
  TF_VAR_create_test_user=false \
  TF_VAR_isolated_e2e_enabled=false \
  TF_VAR_isolated_e2e_namespace= \
  TF_VAR_isolated_e2e_context_memberships='[]' \
  bash "${SCRIPT}" compare --before "${before}" --after "${after}" --output "${comparison}" >/dev/null 2>&1; then
  fail "comparison accepted changed Mailpit database size"
fi
jq -e '.status == "failed" and any(.gates[]; .gate == "mailpit_database_size" and .passed == false)' "${comparison}" >/dev/null

if env \
  WEAVE_DOGFOOD_BOOTSTRAP_ENV="${TMP_DIR}/missing-bootstrap.env" \
  WEAVE_DOGFOOD_DEPLOYMENT_SCOPE=persistent-dogfood \
  TF_VAR_create_test_user=true \
  TF_VAR_isolated_e2e_enabled=false \
  bash "${SCRIPT}" compare --before "${before}" --after "${before}" --output "${comparison}" >/dev/null 2>&1; then
  fail "persistent dogfood accepted the static test user"
fi

# shellcheck disable=SC2016
grep -Fq '"${DOGFOOD_MEMBER_SCRIPT}" status' "${SCRIPT}"
grep -Fq 'weave_mailpit_data' "${SCRIPT}"
grep -Fq '/api/v1/messages' "${SCRIPT}"
grep -Fq '/data/mailpit.db' "${SCRIPT}"
grep -Fq 'databaseSha256' "${SCRIPT}"
grep -Fq '/sessions' "${SCRIPT}"
grep -Fq 'certificate_sha256' "${SCRIPT}"

bootstrap_env="${TMP_DIR}/bootstrap.env"
cat >"${bootstrap_env}" <<'ENV'
export TF_VAR_keycloak_admin_password=fixture-persisted-password
export TF_VAR_caddy_tls_ca_file=/persisted/ca.pem
export TF_VAR_caddy_tls_cert_file=/persisted/cert.pem
export TF_VAR_caddy_tls_key_file=/persisted/key.pem
ENV
(
  export WEAVE_DOGFOOD_BOOTSTRAP_ENV="${bootstrap_env}"
  export TF_VAR_caddy_tls_ca_file=/current/ca.pem
  export TF_VAR_caddy_tls_cert_file=/current/cert.pem
  export TF_VAR_caddy_tls_key_file=/current/key.pem
  # shellcheck source=infra/weave-workspace/persistent-dogfood-observation.sh
  source "${SCRIPT}"
  load_environment
  [[ "${TF_VAR_keycloak_admin_password}" == fixture-persisted-password ]]
  [[ "${TF_VAR_caddy_tls_ca_file}" == /current/ca.pem ]]
  [[ "${TF_VAR_caddy_tls_cert_file}" == /current/cert.pem ]]
  [[ "${TF_VAR_caddy_tls_key_file}" == /current/key.pem ]]
) || fail "persistent bootstrap credentials or current TLS path overrides were not restored"

capture_bin="${TMP_DIR}/capture-bin"
mkdir -p "${capture_bin}"
cat >"${capture_bin}/docker" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
if [[ "${1:-}" == volume && "${2:-}" == inspect ]]; then
  printf 'weave_mailpit_data|fixture-created|/fixture/mailpit\n'
elif [[ "${1:-}" == exec && "$*" == *'wc -c'* ]]; then
  printf '%s\n' "$(printf 'fixture-mailpit-db' | wc -c | tr -d '[:space:]')"
elif [[ "${1:-}" == exec && "$*" == *'cat /data/mailpit.db'* ]]; then
  printf 'fixture-mailpit-db'
else
  exit 1
fi
MOCK
chmod +x "${capture_bin}/docker"
cat >"${capture_bin}/curl" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
printf '{"total":1,"messages":[]}'
MOCK
chmod +x "${capture_bin}/curl"

mailpit_capture="$({
  export PATH="${capture_bin}:${PATH}"
  # shellcheck source=infra/weave-workspace/persistent-dogfood-observation.sh
  source "${SCRIPT}"
  TF_VAR_mailpit_web_host_port=8025 mailpit_summary
})"
expected_database_hash="$(printf 'fixture-mailpit-db' | shasum -a 256 | awk '{print $1}')"
jq -e --arg expectedHash "${expected_database_hash}" '
  .messageCount == 1 and
  .databaseBytes > 0 and
  .databaseSha256 == $expectedHash and
  (.databaseSha256 | test("^[0-9a-f]{64}$"))
' <<<"${mailpit_capture}" >/dev/null || fail "Mailpit capture did not emit the support-safe database hash and size"
if grep -Fq 'fixture-mailpit-db' <<<"${mailpit_capture}"; then
  fail "Mailpit capture leaked database content"
fi

printf 'persistent dogfood observation tests passed\n'
