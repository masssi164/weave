# Matrix Chat migration proof boundary

Status: Sprint 14 contract seed for issues [#537](https://github.com/masssi164/weave/issues/537), [#538](https://github.com/masssi164/weave/issues/538), [#541](https://github.com/masssi164/weave/issues/541), and [#542](https://github.com/masssi164/weave/issues/542). This page defines the conservative proof target and is backed by the machine-readable fixture `specs/0006-portability-contract/matrix-synapse-chat-migration-proof.json`, validated by `./gradlew portabilityContractCheck`.

## Goal

Prove the first no-cost, inspectable Chat portability path using self-hosted Matrix/Synapse behind the Weave Chat domain. The proof must preserve Weave's product boundary: members use Weave Chat and stable capability states; admins/operators review Matrix-specific readiness, export/import, loss, rollback, and audit evidence.

This is not a lossless-migration claim. It is a no-unaccounted-data-loss proof for a named subset.

## Source Matrix objects and events

| Matrix source concept | Candidate Weave Chat canonical object | Initial class | Notes |
| --- | --- | --- | --- |
| Homeserver + user ID (`@user:server`) | Stable chat actor/provider subject mapping | `portable` with mapping | Use immutable provider IDs for traceability; do not expose raw IDs to normal members. |
| Room ID (`!room:server`) and aliases | Conversation/channel mapping | `portable` with mapping | Preserve source room ID in support-safe mapping refs. Alias conflicts require manual review. |
| Room name/topic/avatar state | Conversation metadata | `portable`/`lossy` | Avatar/media durability depends on media strategy. Unsupported state fields go to lossy report. |
| Membership events (`join`, `invite`, `leave`, `ban`, `knock`) | Membership history and current participant state | `portable`/`manual_review` | Current membership is portable; historical edge cases and invite/ban policy require review. |
| Power levels | Role/permission impact report | `manual_review`/`lossy` | Exact Matrix power-level semantics do not map 1:1 to Weave roles. |
| Plain `m.room.message` events | Chat messages | `portable` | Preserve sender, timestamp, body/content subset, source event ID, and room mapping. |
| Media via `mxc://` URIs | Attachment/media reference or copied blob | `portable`/`archive_only`/`manual_review` | Proof must choose copied media or explicit durable references and state retention risk. |
| Redactions | Redacted-message marker | `portable` | Preserve redaction without resurrecting redacted content. |
| Replies/reactions via event relations | Message relation metadata | `portable`/`lossy` | Simple replies/reactions only where referenced events exist and relation mapping is unambiguous. |
| Edits, threads, polls, stickers, widgets, bridged/federated edge cases | Extended Chat semantics | `unsupported`/`coming_later` | Do not silently drop; count and classify in lossy/unsupported reports. |
| Encrypted rooms/history (`m.room.encryption`) | Encrypted Chat history | `unsupported`/`coming_later` | Server-side migration cannot decrypt history without client-side key/export strategy. |

## MVP supported subset

The first Matrix proof may claim support only for:

1. rooms and basic room metadata;
2. users and current membership plus support-safe membership history where available;
3. plain message events with source event IDs, authors, timestamps, room mapping, and redacted content handling;
4. media copied into Weave-controlled storage or explicitly referenced with durability/retention caveats;
5. redactions preserved as redaction markers;
6. simple replies/reactions when source and target events both exist and relation mapping is unambiguous.

## Required migration artifacts

Each dry-run or fixture proof must produce support-safe artifacts aligned with the provider portability contract:

| Artifact | Minimum Matrix content | Release-blocking checks |
| --- | --- | --- |
| ProviderAdapterManifest | `matrix-synapse-chat`, Chat domain, Matrix Client-Server API profile, readiness checks, secret boundary, known limits. | No access tokens, homeserver credentials, raw endpoint URLs with credentials, or downstream raw errors. |
| ProviderMapping | Matrix rooms/users/events/media relations mapped to Weave Chat canonical refs. | Every source object has `portable`, `lossy`, `unsupported`, `manual_review`, `vendor_locked`, or `archive_only` classification. |
| ExportManifest | Counts by rooms, users, membership events, messages, media references/files, redactions, relations. | Stable source IDs and content hashes where allowed; redacted samples only. |
| ImportManifest | Target feasibility, generated target refs, idempotency keys, skipped objects, and post-import validation. | Import cannot apply if blockers or unapproved unsupported/lossy fields remain. |
| LossyMappingReport | Power levels, state fields, unsupported event types, remote-media caveats, relation gaps. | No public claim may say lossless while this report has non-zero counts. |
| PermissionImpactReport | Membership/role consequences and manual decisions. | Member impact must use Weave capability states only. |
| RollbackRetentionReport | Source retention, target rollback feasibility, archive retention, restore-smoke expectations. | Rollback limitations must be shown before cutover confirmation. |
| MigrationAuditRef | Dry-run/apply/rollback request refs, actor class, timestamps, decision, profile/adapter versions. | Audit output remains support-safe. |

The Sprint 14 fixture currently proves classification and apply blocking only. Its sample dry-run is intentionally `blocked` because E2EE history, power-level parity, and media durability decisions require explicit admin/operator review before any apply path can be enabled.

## Admin provider-switch journey

A Matrix Chat switch is only professional if the Admin Console/operator flow includes:

1. **Preflight**: credentials, Matrix API reachability, export permissions, target storage, media policy, E2EE detection, quotas, rate limits, audit sink, backup/archive readiness, and rollback retention.
2. **Consequence preview**: preserved, lossy, unsupported, manual-review, archive-only, and coming_later counts with member-impact copy.
3. **Confirmation**: explicit admin acknowledgement of user disruption, cutover window, unsupported E2EE history, rollback limits, and legal/compliance non-advice.
4. **Cutover**: freeze/sync/export/import/validate steps with idempotency keys and no raw provider payload leakage.
5. **Rollback**: source retention, target cleanup/disable plan, restore-smoke evidence, and manual remediation where rollback cannot restore exact semantics.
6. **Audit/evidence bundle**: support-safe refs that can be linked from release evidence and issue closure.

## Member/client boundary

Normal members must see only Weave Chat and product-level capability states:

- `available`
- `disabled_by_policy`
- `not_configured`
- `degraded`
- `unavailable`
- `coming_later`

Provider-specific Matrix API errors, homeserver URLs, access tokens, power-level diagnostics, media repository internals, and migration reports stay in admin/operator surfaces and support-safe evidence. Accessibility evidence must prove status and disruption copy is screen-reader-friendly, keyboard reachable, and not color-only.

## Non-goals for the first proof

- Lossless migration.
- Server-side migration of encrypted-room history.
- Full Matrix power-level parity.
- Threads, edits, polls, widgets, bridged event semantics, or all federated edge cases.
- Slack/Teams implementation. Slack and Teams remain comparison context because their export/API paths are plan-, license-, compliance-, approval-, throttling-, and billing-constrained.
