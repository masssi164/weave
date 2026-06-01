# Matrix Chat dry-run operator runbook

Status: Sprint 15 support-safe operator evidence for Matrix Chat migration dry-run and Admin provider-switch review.

## Purpose

Use this runbook to produce and interpret Matrix Chat migration dry-run evidence without mutating live providers. Sprint 15 proves reviewable consequences only: Matrix Chat `applyAllowed` remains `false` unless a later spec, issue, and feature gate explicitly allow production apply/cutover.

## Preconditions

- Work from a non-production export or an operator-approved dry-run source snapshot.
- Keep provider credentials as backend-only `SecretRef` values; never paste raw access tokens, homeserver URLs, `mxc://` media IDs, cookies, private keys, or raw provider error bodies into tickets, release notes, or support bundles.
- Confirm the audit sink and support-bundle redaction checks are available before collecting evidence.
- Confirm source retention, rollback archive location, and restore-smoke owner before any future apply discussion.

## Dry-run execution boundary

1. Request backend-owned migration dry-run evidence for the Chat domain.
2. Verify the response contains support-safe `MigrationRun`, `dryRunReportRef`, `providerMappingRef`, `lossyMappingReportRef`, `rollbackArchiveRef`, and audit refs.
3. Verify object counts include rooms/conversations, users, memberships, messages, redactions, relations, media references, power-level data, unsupported custom events, and encrypted-room markers.
4. Verify every source object/field is classified as `portable`, `lossy`, `unsupported`, `manual_review`, `vendor_locked`, or `archive_only`.
5. Treat any missing preflight, stale evidence, raw provider leak, unresolved blocker, unsupported E2EE history, media-retention uncertainty, or power-level uncertainty as apply-blocking.

## Evidence interpretation

| Evidence area | Operator interpretation | Admin/member state |
| --- | --- | --- |
| Plain rooms/messages/memberships | May be portable when ordering, sender refs, timestamps, room refs, redaction tombstones, and relation refs are preserved. | `available` when the dry-run marks the object portable. |
| Matrix power levels | Exact parity is not guaranteed. Unknown, custom, or high-risk mappings require manual review and permission-impact notes. | `degraded` or `unsupported`; never claim perfect role parity. |
| Media | Media may be copied into Weave-controlled storage, retained as archive-only evidence, or left as explicit references with retention caveats. Raw `mxc://` and credential-bearing download URLs are forbidden in support-safe output. | `degraded`, `manual_review`, or `coming_later` until durability is proven. |
| Encrypted-room history | Server migration cannot decrypt E2EE history. Future work needs client-side decrypted export, device trust, key backup/recovery handling, lost-device behavior, and accessible verification/recovery UX. | `unsupported` / `coming_later`; no E2EE history migration claim. |
| Rollback | Rollback means retained source export, target cleanup/disable plan, archive refs, and restore-smoke evidence. It cannot recreate unsupported encrypted history or exact Matrix power-level parity. | `coming_later` for apply/cutover while blockers remain. |

## Failure handling

- If support-safe redaction fails, stop evidence promotion and file a release-blocking defect.
- If `applyAllowed` is true for Sprint 15 Matrix Chat evidence, stop and treat it as a defect unless a later explicit production apply feature gate is linked.
- If manual-review counts are non-zero, keep Admin apply/cutover controls disabled and record the blocker in the Sprint 15 closure evidence.
- If customer wording implies production Matrix migration, lossless migration, legal compliance, or E2EE history migration, reject the wording and rerun release evidence checks.

## Support-safe example summary

- `MigrationRun.state`: `blocked`
- `applyAllowed`: `false`
- `dryRunReportRef`: support-safe ref only
- `memberImpactStates`: `available`, `degraded`, `unsupported`, `coming_later`
- `nextAction`: review consequences, resolve media/power-level/E2EE blockers, keep current Chat provider active
