# Sprint 26 operator recovery evidence

Status: implementation evidence for issues #639, #640, #641, and #642.

## Evidence artifacts

- `infra/weave-workspace/disposable-restore-proof.sh` — executable disposable Backup -> Destroy -> Restore -> Validate rehearsal. It uses only uniquely named `weave_disposable_restore_*` Docker volumes and support-safe fixture domain data.
- `release/provider-lab/operator-recovery/backup-manifest.disposable.json` — disposable proof `BackupManifest` shape with artifact inventory, checksums, privacy boundary, and limitations. Real operator backup artifacts remain private and must not be attached to issues.
- `release/provider-lab/operator-recovery/restore-receipt.disposable.json` — support-safe `RestoreReceipt` from the disposable rehearsal with `validationMode=disposable_stack_rehearsal`, `destroyStep.performed=true`, `domain_data_recovered=passed`, and `releaseEligible=true` for the scoped fixture proof.
- `release/provider-lab/operator-recovery/domain-data-hashes.disposable.json` — support-safe hash proof that seeded fixture domain data matched after backup, destroy, restore, and validate.
- `release/provider-lab/operator-recovery/support-redaction-report.disposable.json` — support-safe redaction report for the disposable proof evidence.
- `release/provider-lab/operator-recovery/sprint-26-scoreboard.json` — claim gate allowing only the scoped disposable restore-proof wording and preserving production/private-evidence limitations.
- `docs/operator-recovery-known-limitations.md` — operator KnownLimitations linked by the scoreboard and release wording.
- `tools/operator_recovery_check.py` — executable gate for artifacts, disposable restore receipt, redaction, limitations, and scoreboard agreement.

## Current closure truth

Sprint 26 recovery now has a support-safe disposable restore proof for fixture domain data. The checked-in receipt is release-eligible only for the scoped claim that Weave has executable guardrails and a disposable Backup -> Destroy -> Restore -> Validate proof. It does not claim that production customer data has been restored, that E2EE lost-device recovery is solved, or that private backup artifacts can be shared through support channels.

## Re-run path

An operator may re-run the proof on an approved disposable, non-production machine:

1. Run `WEAVE_DISPOSABLE_RESTORE_RUN_ID=<id> bash infra/weave-workspace/disposable-restore-proof.sh`.
2. Confirm the output directory contains `BackupManifest.json`, `RestoreReceipt.json`, `domain-data-hashes.json`, and `support-redaction-report.json`.
3. Run `python3 tools/operator_recovery_check.py --evidence-dir <output-dir>`; the directory must contain the generated private-shape manifest and backup artifacts for local verification, but only support-safe receipts/redaction reports may be attached externally.
4. Keep production restore rehearsals as separate operator-approved events with private backup storage and site-specific review.
