#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT="${ROOT_DIR}/activate-user.sh"

evidence_file="$(mktemp)"
output="$(${SCRIPT} \
  --dry-run \
  --username alice \
  --email alice@example.test \
  --display-name 'Alice Example' \
  --role admin \
  --invite-ref activation-alice-s32 \
  --activation-lifespan 900 \
  --evidence-file "${evidence_file}")"

grep -Fq 'Weave activation invite plan' <<<"${output}"
grep -Fq -- '- username: alice' <<<"${output}"
grep -Fq -- '- email: alice@example.test' <<<"${output}"
grep -Fq -- '- role: admin' <<<"${output}"
grep -Fq -- '- group: workspace-admins' <<<"${output}"
grep -Fq -- '- invite ref: activation-alice-s32' <<<"${output}"
grep -Fq -- '- activation mode: Keycloak required-action email' <<<"${output}"
grep -Fq -- '- required actions: VERIFY_EMAIL,UPDATE_PASSWORD' <<<"${output}"
grep -Fq -- '- QR/deeplink secrets: none' <<<"${output}"
grep -Fq 'WEAVE_ACTIVATION_INVITE_DRY_RUN inviteRef=activation-alice-s32 supportSafe=true' <<<"${output}"
grep -Fq 'Dry run only: Keycloak was not modified.' <<<"${output}"

if grep -Eiq 'password:|initial password|not-secret-for-dry-run|token|client_secret' <<<"${output}"; then
  echo 'dry-run output leaked credential-like activation material' >&2
  exit 1
fi

jq -e '
  .schemaVersion == "weave.dogfood.activation-invite.v1"
  and .inviteRef == "activation-alice-s32"
  and .activation.mode == "keycloak-required-actions-email"
  and .activation.requiredActions == ["VERIFY_EMAIL", "UPDATE_PASSWORD"]
  and .activation.lifespanSeconds == 900
  and .activation.mailSent == false
  and .qrOrDeeplinkCarriesSecret == false
  and .appStoresActivationSecret == false
  and .supportSafe == true
  and (.usernameSha256 | test("^[0-9a-f]{64}$"))
  and (.emailSha256 | test("^[0-9a-f]{64}$"))
' "${evidence_file}" >/dev/null
if grep -Fq 'alice@example.test' "${evidence_file}" || grep -Fq 'Alice Example' "${evidence_file}"; then
  echo 'support-safe evidence leaked direct identity fields' >&2
  exit 1
fi
rm -f "${evidence_file}"

guest_output="$(${SCRIPT} \
  --dry-run \
  --username guest1 \
  --email guest1@example.test \
  --display-name 'Guest Example' \
  --role guest)"

grep -Fq -- '- role: guest' <<<"${guest_output}"
grep -Fq -- '- group: workspace-guests' <<<"${guest_output}"
if grep -Eq -- '- (role|group): .*workspace-(members|admins)|- role: (member|admin)' <<<"${guest_output}"; then
  echo 'guest dry-run received member/admin role or group' >&2
  exit 1
fi

if ${SCRIPT} --dry-run --username alice --email alice@example.test --display-name 'Alice Example' --role member --password 'not-secret-for-dry-run' >/tmp/weave-activate-password.out 2>&1; then
  echo 'password-based activation was accepted' >&2
  exit 1
fi
grep -Fq 'is no longer supported' /tmp/weave-activate-password.out
rm -f /tmp/weave-activate-password.out

if ${SCRIPT} --dry-run --username alice --email alice@example.test --display-name 'Alice Example' --role member --required-actions VERIFY_EMAIL >/tmp/weave-activate-required-actions.out 2>&1; then
  echo 'activation without UPDATE_PASSWORD was accepted' >&2
  exit 1
fi
grep -Fq -- '--required-actions must include UPDATE_PASSWORD' /tmp/weave-activate-required-actions.out
rm -f /tmp/weave-activate-required-actions.out

if ${SCRIPT} --dry-run --username alice --email alice@example.test --display-name 'Alice Example' --role superuser >/tmp/weave-activate-invalid.out 2>&1; then
  echo 'invalid role was accepted' >&2
  exit 1
fi
grep -Fq "Invalid role 'superuser'" /tmp/weave-activate-invalid.out
rm -f /tmp/weave-activate-invalid.out

printf 'activate-user helper tests passed\n'
