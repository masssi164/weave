# Sprint 26 operator recovery evidence

Status: implementation evidence for issues #639, #640, #641, #642, and the Sprint 32 refresh in #712.

## Evidence artifacts

- `infra/weave-workspace/disposable-restore-proof.sh` — executable disposable Backup -> Destroy -> Restore -> Validate rehearsal. It uses only uniquely named `weave_disposable_restore_*` Docker volumes and support-safe fixture domain data.
- `release/provider-lab/operator-recovery/backup-manifest.disposable.json` — disposable proof `BackupManifest` shape with artifact inventory, checksums, privacy boundary, and limitations. Real operator backup artifacts remain private and must not be attached to issues.
- `release/provider-lab/operator-recovery/restore-receipt.disposable.json` — support-safe `RestoreReceipt` shape from the disposable rehearsal with `validationMode=disposable_stack_rehearsal`, `destroyStep.performed=true`, `domain_data_recovered=passed`, and explicit fixture-only limitations.
- `release/provider-lab/operator-recovery/domain-data-hashes.disposable.json` — support-safe hash proof that seeded fixture domain data matched after backup, destroy, restore, and validate.
- `release/provider-lab/operator-recovery/support-redaction-report.disposable.json` — support-safe redaction report for the disposable proof evidence.
- `release/provider-lab/operator-recovery/sprint-26-scoreboard.json` — claim gate allowing only the scoped disposable restore-proof wording and preserving production/private-evidence limitations.
- `docs/operator-recovery-known-limitations.md` — operator KnownLimitations linked by the scoreboard and release wording.
- `tools/operator_recovery_check.py` — executable gate for artifacts, disposable restore receipt, redaction, limitations, and scoreboard agreement.

## Current closure truth

Sprint 32 refreshed the disposable recovery proof for issue #712. The generated `BackupManifest.json`, `RestoreReceipt.json`, `domain-data-hashes.json`, and `support-redaction-report.json` are support-safe fixture-domain artifacts when produced by `infra/weave-workspace/disposable-restore-proof.sh`; real operator backup artifacts remain private. The refreshed receipt sets `releaseEligible=false` and `releaseReadinessClaim=false` so this evidence can support a bounded non-production restore statement without claiming production rollback, lossless migration, E2EE history migration, or customer/release readiness.

Portability evidence remains separate and dry-run/fixture scoped: `python3 tools/portability_contract_check.py`, `python3 tools/cross_domain_provider_proof_check.py`, and `python3 tools/provider_lab_check.py` must pass before citing portability or no-unaccounted-data-loss evidence. Passing those gates does not authorize live provider mutation; any destructive or live restore action still requires an explicit operator approval gate and private evidence review.

## Re-run path

An operator may re-run the proof on an approved disposable, non-production machine:

1. Run `WEAVE_DISPOSABLE_RESTORE_RUN_ID=<id> bash infra/weave-workspace/disposable-restore-proof.sh`.
2. Confirm the output directory contains `BackupManifest.json`, `RestoreReceipt.json`, `domain-data-hashes.json`, and `support-redaction-report.json`.
3. Run the portability/no-unaccounted-data-loss companion gates when making a portability statement: `python3 tools/portability_contract_check.py`, `python3 tools/cross_domain_provider_proof_check.py`, and `python3 tools/provider_lab_check.py`.
4. Keep production restore rehearsals as separate operator-approved events with private backup storage and site-specific review.
