#!/usr/bin/env bash
# shellcheck shell=bash
# Assertions in this file match literal GitHub workflow expressions.
# shellcheck disable=SC2016

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../.." && pwd)"
WORKFLOW="${ROOT_DIR}/.github/workflows/dogfood-pending-identity-recovery.yml"
MEMBER_HELPER="${ROOT_DIR}/infra/weave-workspace/dogfood-member.sh"
IDENTITY_AUDIT="${ROOT_DIR}/infra/weave-workspace/audit-private-backup-identity.sh"
OPERATOR_RUNBOOK="${ROOT_DIR}/infra/docs/operator-runbook.md"

fail() { printf '%s\n' "$*" >&2; exit 1; }
require_workflow() { grep -Fq -- "$1" "${WORKFLOW}" || fail "Recovery workflow is missing: $1"; }
require_helper() { grep -Fq -- "$1" "${MEMBER_HELPER}" || fail "Member helper is missing: $1"; }
require_runbook() { grep -Fq -- "$1" "${OPERATOR_RUNBOOK}" || fail "Operator runbook is missing: $1"; }
assert_workflow_order() {
  local first="$1" second="$2" first_line second_line
  first_line="$(grep -nF -- "$first" "${WORKFLOW}" | head -1 | cut -d: -f1)"
  second_line="$(grep -nF -- "$second" "${WORKFLOW}" | head -1 | cut -d: -f1)"
  [[ -n "$first_line" && -n "$second_line" && "$first_line" -lt "$second_line" ]] ||
    fail "Recovery workflow must place '$first' before '$second'"
}

[[ -f "${WORKFLOW}" ]] || fail "Pending identity recovery workflow is missing"
[[ -f "${MEMBER_HELPER}" ]] || fail "Persistent member helper is missing"
[[ -x "${IDENTITY_AUDIT}" ]] || fail "Private backup identity audit helper is missing or not executable"
[[ -f "${OPERATOR_RUNBOOK}" ]] || fail "Operator runbook is missing"
require_workflow 'group: weave-live-mac-mini-exclusive'
require_workflow 'cancel-in-progress: false'
require_workflow 'environment: dogfood'
require_workflow 'EXPECTED_RUNNER_NAME: weave-live-mac-mini'
require_workflow 'DISPATCH_REF: ${{ github.ref_name }}'
require_workflow '[[ "$DISPATCH_REF" == dogfood ]]'
require_workflow 'retire-lost-pending-identity'
require_workflow 'retire-restored-test-bootstrap'
require_workflow 'successful exact-candidate isolated E2E'
require_workflow '[[ "$(git rev-parse HEAD)" == "$CANDIDATE_SHA" ]]'
require_workflow 'git merge-base --is-ancestor "$CANDIDATE_SHA" origin/dogfood'
require_workflow '.specCorpus.gitCommit'
require_workflow 'WEAVE_REMOVE_VOLUMES: '\''false'\'''
require_workflow 'WEAVE_KEYCLOAK_SUPERVISOR: ${{ vars.WEAVE_KEYCLOAK_SUPERVISOR }}'
require_workflow 'WEAVE_KEYCLOAK_REVIEWED_ENV_FILE: ${{ vars.WEAVE_KEYCLOAK_REVIEWED_ENV_FILE }}'
require_workflow 'WEAVE_ENV_FILE: ${{ vars.WEAVE_KEYCLOAK_REVIEWED_ENV_FILE }}'
require_workflow 'Protected recovery requires externally installed Keycloak supervisor and reviewed environment paths.'
require_workflow 'reviewed dogfood environment must be root-owned mode 0444 or 0644'
require_workflow 'org.opencontainers.image.revision=$WEAVE_IMAGE_SOURCE_COMMIT'
require_workflow "'+refs/heads/dev:refs/remotes/origin/dev'"
require_workflow 'tools/candidate_source_mapping.py'
require_workflow 'candidate-source-mapping.json'
require_workflow '--image "keycloak-sanitizer=$WEAVE_KEYCLOAK_SANITIZER_IMAGE"'
if grep -Fq 'org.opencontainers.image.revision=$CANDIDATE_SHA' "${WORKFLOW}"; then
  fail "Recovery images must identify the protected dev source, not the lane commit"
fi
require_workflow './dogfood-member.sh recover-lost-pending'
require_workflow './dogfood-member.sh retire-restored-bootstrap'
require_workflow 'restored-bootstrap-retirement.json'
require_workflow 'bootstrap-state-detachment.json'
require_workflow './compose.sh dogfood keycloak-apply'
require_workflow './compose.sh dogfood keycloak-verify'
require_workflow 'providerMutationPerformed:false'
require_workflow 'weave.dogfood.private-backup-identity-audit-set.v1'
require_workflow './audit-private-backup-identity.sh "$backup_candidate"'
require_workflow 'auditedBackupCount:length'
require_workflow '[[ -n "$backup_candidate" && -s "$backup_candidate/BackupManifest.json" ]]'
require_workflow 'persistent_volumes=('
require_workflow 'weave_matrix_chat_appservice_runtime'
require_workflow '.isolatedDatabaseReplay.persistentRuntimeMutated == false'
require_workflow '.identity.identityRestorableForRecordedMember == false'
require_workflow '.identity.recoveryIdentityBoundaryAccepted == true'
require_workflow 'bootstrapRetirementRequired:$latest[0].identity.bootstrapRetirementRequired'
require_workflow 'not_required_empty_human_boundary'
require_workflow 'providerMutationPerformed:false'
require_workflow 'privateBackupIdentityAuditStatus:$identityAuditStatus'
require_workflow '--prior-evidence "$prior_evidence"'
require_workflow '--approval-ref "$GITHUB_SERVER_URL/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID"'
require_helper 'retired-pending-identities'
require_helper 'verify_recorded_subject_absent'
require_helper 'realm_human_users'
require_helper 'keycloak-admin-api-exact-subject'
require_helper 'find "${evidence_dir}" -maxdepth 2'
require_workflow 'WEAVE_BACKUP_ROOT="$backup_root" bash ./backup.sh dogfood'
require_workflow "-name 'weave-dogfood-*'"
require_workflow 'bash ./restore-smoke.sh "$backup_dir"'
require_workflow '.provesRestoredDomainData == false'
require_workflow 'restoredDomainDataProven:false'
require_workflow 'humanTestingReady:false'
require_workflow 'two-install-dogfood-deployment-required'
require_workflow 'protected-recovery-incomplete'
require_workflow 'private-backup-or-restore-smoke-failed'
require_workflow 'path: ${{ env.WEAVE_RECOVERY_EVIDENCE_DIR }}'
require_runbook 'Restoring the private Keycloak database is always the first recovery path.'
require_runbook 'not identity-restorable for that member'
require_runbook 'humanTestingReady=false'
require_runbook 'the human tester completes the Keycloak activation'
require_runbook 'run the standard `Test Stack Deploy` workflow for the same candidate'
assert_workflow_order 'Prove the fixed desired state does not own human identities' 'Bootstrap lost persistent runtime with the exact candidate'
assert_workflow_order 'Bootstrap lost persistent runtime with the exact candidate' 'Retire the proven restored disposable bootstrap identity'
assert_workflow_order 'Create private pre-recovery backup and audit identity-restorability' 'Retire the proven restored disposable bootstrap identity'

upload_block="$(sed -n '/- name: Upload support-safe recovery evidence/,$p' "${WORKFLOW}")"
if [[ "$(grep -Fc 'uses: actions/upload-artifact@' "${WORKFLOW}")" -ne 1 ]]; then
  fail "Recovery workflow may upload only its one support-safe evidence directory"
fi
if grep -Eq 'backup_root|backup_dir|BackupManifest\.json|RestoreReceipt\.json' <<<"${upload_block}"; then
  fail "Private backup artifacts must never be uploaded as support evidence"
fi
if grep -Fq 'WEAVE_REMOVE_VOLUMES: '\''true'\''' "${WORKFLOW}"; then
  fail "Pending recovery must not permit destructive volume removal"
fi
if grep -Eq 'ci-(db|keycloak|mas|synapse|nextcloud|matrix)' "${WORKFLOW}"; then
  fail "Pending recovery must use restored persistent credentials, not CI placeholder credentials"
fi
if grep -Fq 'mapfile' "${WORKFLOW}"; then
  fail "Pending recovery must remain compatible with the macOS system Bash"
fi
if grep -Fq 'teardown.sh' "${WORKFLOW}"; then
  fail "Pending recovery must never tear down the persistent runtime"
fi
if [[ "$(grep -Fc "if: \${{ always() && env.WEAVE_RECOVERY_EVIDENCE_DIR != '' }}" "${WORKFLOW}")" -lt 2 ]]; then
  fail "Recovery outcome and partial support-safe evidence must be retained on failure"
fi

printf 'DOGFOOD_PENDING_IDENTITY_RECOVERY_CONTRACT status=passed evidenceMode=offline-spec supportSafe=true\n'
