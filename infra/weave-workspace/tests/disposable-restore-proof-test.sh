#!/usr/bin/env bash
# shellcheck shell=bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
readonly ROOT_DIR
SCRIPT="${ROOT_DIR}/disposable-restore-proof.sh"

[[ -x "${SCRIPT}" ]] || { echo "disposable restore proof script is not executable" >&2; exit 1; }

grep -Fq 'weave_disposable_restore_' "${SCRIPT}" || { echo "script must use disposable-only volume prefix" >&2; exit 1; }
grep -Fq 'assert_disposable_scope' "${SCRIPT}" || { echo "script must assert disposable scope" >&2; exit 1; }
grep -Fq 'cleanup_volumes' "${SCRIPT}" || { echo "script must clean up disposable volumes" >&2; exit 1; }
grep -Fq 'no_production_volumes_touched' "${SCRIPT}" || { echo "receipt must record production-volume guard" >&2; exit 1; }
if grep -Eq 'docker volume rm (weave_(db|synapse|nextcloud|keycloak|caddy)|\$\{?TF_VAR_tenant_slug)' "${SCRIPT}"; then
  echo "script appears to remove non-disposable Weave volumes" >&2
  exit 1
fi

if [[ "${WEAVE_RUN_DOCKER_DISPOSABLE_RESTORE_TEST:-false}" == "true" ]]; then
  output_parent="$(mktemp -d)"
  trap 'rm -rf "${output_parent}"' EXIT
  WEAVE_DISPOSABLE_RESTORE_RUN_ID=test-static-proof bash "${SCRIPT}" "${output_parent}" >/tmp/disposable-restore-proof-test.out
  receipt="${output_parent}/test-static-proof/RestoreReceipt.json"
  [[ -s "${receipt}" ]] || { echo "script did not write RestoreReceipt" >&2; cat /tmp/disposable-restore-proof-test.out >&2; exit 1; }
  python3 - "${receipt}" <<'PY'
import json
import sys
receipt = json.load(open(sys.argv[1], encoding='utf-8'))
assert receipt['validationMode'] == 'disposable_stack_rehearsal'
assert receipt['destroyStep']['performed'] is True
assert receipt['provesRestoredDomainData'] is True
assert receipt['releaseEligible'] is False
assert receipt['releaseReadinessClaim'] is False
assert 'production rollback' in ' '.join(receipt['limitations']).lower()
PY
fi

grep -Fq '"supportSafe": True' "${SCRIPT}" || { echo "manifest/receipt must remain support-safe" >&2; exit 1; }
grep -Fq 'releaseReadinessClaim' "${SCRIPT}" || { echo "receipt must explicitly block release readiness claims" >&2; exit 1; }
if grep -Eq 'release-ready|customer-ready|lossless migration claim|production rollback claim' "${SCRIPT}"; then
  echo "script wording must not imply release/customer-ready or production/lossless claims" >&2
  exit 1
fi

printf 'disposable restore proof tests passed\n'
