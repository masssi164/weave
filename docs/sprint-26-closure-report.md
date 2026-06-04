# Sprint 26 closure report — Operator Recovery

Status: closed for the scoped disposable recovery proof.

## Governing scope

- GitHub milestone: Sprint 26 — Operator Recovery.
- Issues: #639 backup manifest, #640 support-safe redaction, #641 known limitations, #642 restore-proof release gate.
- Product boundary: no production mutation without explicit approval; backup artifacts with secrets or member data stay private; support evidence must be redacted and scoped.

## Issue DAG final state

| Issue | Role | Final state |
| --- | --- | --- |
| #639 | Private backup manifest and artifact inventory | Covered by `backup-manifest.disposable.json` shape and `backup.sh` private artifact path. |
| #640 | Support-safe redaction evidence | Covered by `support-redaction-report.disposable.json` and support-bundle redaction checks. |
| #641 | KnownLimitations and wording guard | Covered by `docs/operator-recovery-known-limitations.md`. |
| #642 | Block release on missing restore proof | Covered by disposable Backup -> Destroy -> Restore -> Validate proof and release-eligible `RestoreReceipt`. |

## Restore proof evidence

Command run on a non-production local disposable proof path:

```sh
WEAVE_DISPOSABLE_RESTORE_RUN_ID=issue-642-local-proof bash infra/weave-workspace/disposable-restore-proof.sh
```

Support-safe checked-in evidence:

- `infra/weave-workspace/disposable-restore-proof.sh`
- `release/provider-lab/operator-recovery/backup-manifest.disposable.json`
- `release/provider-lab/operator-recovery/restore-receipt.disposable.json`
- `release/provider-lab/operator-recovery/domain-data-hashes.disposable.json`
- `release/provider-lab/operator-recovery/support-redaction-report.disposable.json`
- `release/provider-lab/operator-recovery/sprint-26-scoreboard.json`
- `docs/evidence/operator-recovery-report.md`
- `docs/operator-recovery-known-limitations.md`

The receipt records `validationMode=disposable_stack_rehearsal`, `destroyStep.performed=true`, `domain_data_recovered=passed`, `provesRestoredDomainData=true`, and `releaseEligible=true` for fixture domain data. The script creates only uniquely named `weave_disposable_restore_*` Docker volumes and removes them after the run.

## Gates

- `python3 tools/operator_recovery_check.py`
- `bash infra/weave-workspace/tests/disposable-restore-proof-test.sh`
- `python3 tools/operator_recovery_check.py --evidence-dir infra/weave-workspace/.generated/disposable-restore-proof/issue-642-local-proof`

## Release wording

Allowed: Sprint 26 has executable recovery guardrails and a support-safe disposable Backup -> Destroy -> Restore -> Validate proof for fixture domain data.

Not allowed: production/customer-data restore readiness, E2EE lost-device recovery, or support-bundle sharing without operator review.

## Remaining limitations

Production restore rehearsals remain operator-approved events with private backup storage and site-specific evidence. See `docs/operator-recovery-known-limitations.md`.
