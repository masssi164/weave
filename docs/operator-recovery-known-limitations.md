# Operator recovery KnownLimitations

Status: Sprint 26 operator-recovery guardrail for issues #639, #640, #641, and #642.

This page is the support-safe limitations reference for Weave backup, restore, and support-bundle evidence. It prevents release wording from overstating the current proof.

## Current proof boundary

- `infra/weave-workspace/backup.sh` can create private operator backup artifacts and a `BackupManifest.json` with scope, artifact names, sizes, and checksums.
- `infra/weave-workspace/restore-smoke.sh` can create a `RestoreReceipt.json` for artifact preflight or post-restore readiness checks.
- Checked-in Sprint 26 disposable evidence proves the artifact contracts, release gate behavior, and a non-production Backup -> Destroy -> Restore -> Validate rehearsal for support-safe fixture domain data.
- Production/customer-data restore wording remains blocked until an operator-approved production-like rehearsal keeps private backup artifacts under operator control and records site-specific limitations.

## Known limitations by domain

| Area | Limitation | Required promotion evidence |
| --- | --- | --- |
| History | Chat/event history restore is only proven when the restored service data is checked after a destroy step. | RestoreReceipt with `destroyStep.performed=true` and domain-data checks passed. |
| Attachments/media | Matrix media and Nextcloud file blobs are archive artifacts; fixture-only checks do not prove user-visible attachment recovery. | Post-restore object/file checks and support-safe counts/hashes. |
| Provider-specific data | Provider internals, custom events, unsupported metadata, and lossy fields may not round-trip. | Provider-specific loss/limitations report and no-unaccounted-data-loss accounting. |
| E2EE archives | Server-side restore cannot prove decrypted Matrix E2EE history or lost-device recovery. | Accepted E2EE export/key recovery strategy plus accessible recovery evidence. |
| Conflicts | Concurrent writes, identity remapping, and deleted-object conflicts are not resolved by the artifact preflight. | Conflict report with operator decision/audit references. |
| Weaver memory | Private Weaver memory is excluded from support bundles by default and must not be restored or disclosed through support diagnostics. | Separate governed memory export/delete/restore policy and audited authorization. |
| Support bundles | Support bundles are redacted diagnostics, not backups, and may still require operator review before sharing. | Redaction report with negative fixture plus site-specific review record. |

## Release wording rule

Allowed wording must say that Sprint 26 adds executable guardrails and a support-safe disposable restore proof for fixture domain data. Do not claim production/customer-data backup and restore readiness unless a separate operator-approved evidence directory passes `python3 tools/operator_recovery_check.py --evidence-dir <operator-evidence-dir>` and the release wording names the covered scope.
