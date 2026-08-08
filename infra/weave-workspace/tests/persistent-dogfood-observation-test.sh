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
  "schemaVersion":"weave.persistent-dogfood-observation.v2",
  "deploymentScope":"persistent-dogfood",
  "e2eStackScope":"persistent",
  "isolatedRunBound":false,
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
  WEAVE_E2E_STACK_SCOPE=persistent \
  bash "${SCRIPT}" compare --before "${before}" --after "${after}" --output "${comparison}" >/dev/null
jq -e '.status == "passed" and .baselineSource == "pre-deploy" and .preExistingRuntimeObserved == true and .twoNonDestructiveInstallsPreservedState == true and all(.gates[]; .passed)' "${comparison}" >/dev/null

env \
  WEAVE_DOGFOOD_BOOTSTRAP_ENV="${TMP_DIR}/missing-bootstrap.env" \
  WEAVE_E2E_STACK_SCOPE=persistent \
  WEAVE_PERSISTENT_BASELINE_SOURCE=first-install \
  bash "${SCRIPT}" compare --before "${before}" --after "${after}" --output "${comparison}" >/dev/null
jq -e '.status == "passed" and .baselineSource == "first-install" and .preExistingRuntimeObserved == false and .twoNonDestructiveInstallsPreservedState == true' "${comparison}" >/dev/null

jq '.tls.leafSha256 = "changed-leaf"' "${before}" >"${after}"
if env \
  WEAVE_DOGFOOD_BOOTSTRAP_ENV="${TMP_DIR}/missing-bootstrap.env" \
  WEAVE_E2E_STACK_SCOPE=persistent \
  bash "${SCRIPT}" compare --before "${before}" --after "${after}" --output "${comparison}" >/dev/null 2>&1; then
  fail "comparison accepted a changed TLS identity"
fi
jq -e '.status == "failed" and any(.gates[]; .gate == "tls_leaf" and .passed == false)' "${comparison}" >/dev/null

jq '.mailpit.databaseSha256 = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"' "${before}" >"${after}"
if env \
  WEAVE_DOGFOOD_BOOTSTRAP_ENV="${TMP_DIR}/missing-bootstrap.env" \
  WEAVE_E2E_STACK_SCOPE=persistent \
  bash "${SCRIPT}" compare --before "${before}" --after "${after}" --output "${comparison}" >/dev/null 2>&1; then
  fail "comparison accepted changed Mailpit database content"
fi
jq -e '.status == "failed" and any(.gates[]; .gate == "mailpit_database_hash" and .passed == false)' "${comparison}" >/dev/null

jq '.mailpit.databaseBytes = 8192' "${before}" >"${after}"
if env \
  WEAVE_DOGFOOD_BOOTSTRAP_ENV="${TMP_DIR}/missing-bootstrap.env" \
  WEAVE_E2E_STACK_SCOPE=persistent \
  bash "${SCRIPT}" compare --before "${before}" --after "${after}" --output "${comparison}" >/dev/null 2>&1; then
  fail "comparison accepted changed Mailpit database size"
fi
jq -e '.status == "failed" and any(.gates[]; .gate == "mailpit_database_size" and .passed == false)' "${comparison}" >/dev/null

if env \
  WEAVE_DOGFOOD_BOOTSTRAP_ENV="${TMP_DIR}/missing-bootstrap.env" \
  WEAVE_E2E_STACK_SCOPE=isolated \
  bash "${SCRIPT}" compare --before "${before}" --after "${before}" --output "${comparison}" >/dev/null 2>&1; then
  fail "persistent dogfood accepted the isolated Compose scope"
fi

# shellcheck disable=SC2016
grep -Fq '"${DOGFOOD_MEMBER_SCRIPT}" status' "${SCRIPT}"
grep -Fq 'WEAVE_MAILPIT_DATA_VOLUME:-weave_dogfood_mailpit_data' "${SCRIPT}"
grep -Fq 'WEAVE_RESOURCE_PREFIX:-weave-dogfood' "${SCRIPT}"
grep -Fq '/api/v1/messages' "${SCRIPT}"
grep -Fq '/data/mailpit.db' "${SCRIPT}"
grep -Fq 'databaseSha256' "${SCRIPT}"
grep -Fq '/sessions' "${SCRIPT}"
grep -Fq 'certificate_sha256' "${SCRIPT}"

bootstrap_env="${TMP_DIR}/bootstrap.env"
cat >"${bootstrap_env}" <<'ENV'
export WEAVE_KEYCLOAK_ADMIN_PASSWORD=fixture-persisted-password
export WEAVE_CADDY_TLS_CA_FILE=/persisted/ca.pem
export WEAVE_CADDY_TLS_CERT_FILE=/persisted/cert.pem
export WEAVE_CADDY_TLS_KEY_FILE=/persisted/key.pem
export WEAVE_E2E_STACK_SCOPE=isolated
export WEAVE_E2E_RUN_ID=stale-isolated-run
export WEAVE_E2E_RUN_NAMESPACE=weave-e2e-stale
ENV
(
  export WEAVE_DOGFOOD_BOOTSTRAP_ENV="${bootstrap_env}"
  export WEAVE_CADDY_TLS_CA_FILE=/current/ca.pem
  export WEAVE_CADDY_TLS_CERT_FILE=/current/cert.pem
  export WEAVE_CADDY_TLS_KEY_FILE=/current/key.pem
  export WEAVE_E2E_STACK_SCOPE=persistent
  # shellcheck source=infra/weave-workspace/persistent-dogfood-observation.sh
  source "${SCRIPT}"
  load_environment
  [[ "${WEAVE_KEYCLOAK_ADMIN_PASSWORD}" == fixture-persisted-password ]]
  [[ "${WEAVE_CADDY_TLS_CA_FILE}" == /current/ca.pem ]]
  [[ "${WEAVE_CADDY_TLS_CERT_FILE}" == /current/cert.pem ]]
  [[ "${WEAVE_CADDY_TLS_KEY_FILE}" == /current/key.pem ]]
  [[ "${WEAVE_E2E_STACK_SCOPE}" == persistent ]]
  [[ -z "${WEAVE_E2E_RUN_ID:-}" ]]
  [[ -z "${WEAVE_E2E_RUN_NAMESPACE:-}" ]]
) || fail "persistent bootstrap credentials or current TLS path overrides were not restored"

if (
  export WEAVE_DOGFOOD_BOOTSTRAP_ENV="${bootstrap_env}"
  export WEAVE_E2E_STACK_SCOPE=persistent
  export WEAVE_E2E_RUN_ID=caller-supplied-isolated-run
  # shellcheck source=infra/weave-workspace/persistent-dogfood-observation.sh
  source "${SCRIPT}"
  load_environment
  assert_persistent_scope
) >/dev/null 2>&1; then
  fail "persistent scope accepted caller-supplied isolated E2E contamination"
fi

capture_bin="${TMP_DIR}/capture-bin"
mkdir -p "${capture_bin}"
cat >"${capture_bin}/docker" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
if [[ "${1:-}" == volume && "${2:-}" == inspect ]]; then
  [[ "${3:-}" == fixture_dogfood_mailpit_data ]]
  printf 'fixture_dogfood_mailpit_data|fixture-created|/fixture/mailpit\n'
elif [[ "${1:-}" == exec && "$*" == *'wc -c'* ]]; then
  [[ "${2:-}" == fixture-dogfood-mailpit ]]
  printf '%s\n' "$(printf 'fixture-mailpit-db' | wc -c | tr -d '[:space:]')"
elif [[ "${1:-}" == exec && "$*" == *'cat /data/mailpit.db'* ]]; then
  [[ "${2:-}" == fixture-dogfood-mailpit ]]
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
  WEAVE_MAILPIT_DATA_VOLUME=fixture_dogfood_mailpit_data \
    WEAVE_RESOURCE_PREFIX=fixture-dogfood \
    WEAVE_MAILPIT_WEB_HOST_PORT=8025 \
    mailpit_summary
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
