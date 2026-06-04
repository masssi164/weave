# Sprint 26 operator recovery evidence

Status: implementation evidence for issues #639, #640, #641, and #642.

## Evidence artifacts

- `release/provider-lab/operator-recovery/backup-manifest.fixture.json` — `BackupManifest` shape with restore scope, artifact inventory, checksums, privacy boundary, and limitations.
- `release/provider-lab/operator-recovery/restore-receipt.fixture.json` — truthful `RestoreReceipt` fixture for artifact preflight only; it does not claim a live restore.
- `release/provider-lab/operator-recovery/support-redaction-report.fixture.json` — redaction report fixture with checks and negative-fixture coverage.
- `release/provider-lab/operator-recovery/sprint-26-scoreboard.json` — claim gate and release blocker for missing live destroy/restore proof.
- `docs/operator-recovery-known-limitations.md` — operator KnownLimitations linked by the scoreboard and release wording.
- `tools/operator_recovery_check.py` — executable gate for artifacts, redaction, limitations, and release-blocking behavior.

## Current closure truth

Sprint 26 recovery is **not release-ready** from checked-in fixtures alone. The implemented gate blocks release wording because the checked-in receipt uses `validationMode=artifacts_only`, has `destroyStep.performed=false`, and does not prove restored domain data.

## Manual promotion path

An operator may promote the evidence only on an approved disposable, non-production stack:

1. Run `infra/weave-workspace/backup.sh` and keep the backup private.
2. Destroy only fixture/disposable state under explicit operator approval.
3. Restore from the backup.
4. Run `infra/weave-workspace/restore-smoke.sh <backup-dir>` on the restored stack.
5. Preserve support-safe `BackupManifest.json`, `RestoreReceipt.json`, and `support-redaction-report.json` references.
6. Run `python3 tools/operator_recovery_check.py --evidence-dir <support-safe-evidence-dir>`.

Until that gate passes with `provesRestoredDomainData=true`, release wording must reference `docs/operator-recovery-known-limitations.md` and keep the restore-proof blocker open.
