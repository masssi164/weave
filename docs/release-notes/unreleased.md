# Unreleased

Use this page for release-affecting changes that have merged but are not included in a tagged release yet.

## Added

- Context-driven workflow primitives now have a provider-neutral, linear-first preview contract with explicit context references, blocker/evidence metadata, sample workflows, and dry-run-only governed agent participation.
- Contextual meetings now have a fail-closed architecture contract preserving LiveKit as the active meetings provider contract while documenting encryption boundaries, consent defaults, and accessible join requirements before media controls are enabled.
- Sprint 8/Sprint 9 acceptance now includes mapped product-readiness waterfall evidence for domain registry review, Keycloak dry-run, provider apply blocking, portability reports, Calls/LiveKit readiness, Weaver approvals, member opt-in, and support-safe release blockers.
- Sprint 11 Live Stack acceptance now maps a provider-reality vertical for Files, Calendar, Boards, Calls, and Documents with live-runtime evidence separated from manual accessibility accounting.
- Sprint 12 adds provider portability schema v2 fixtures and reports for Files, Calendar, Boards, and Chat, plus Office/WOPI, Weaver isolation, Weaver registry, identity lifecycle, accessibility, and operator lifecycle contracts.
- Weaver/OpenClaw release documentation now clarifies the signed RuntimeProfile boundary: Weave projects admin-governed Chat domain providers, model aliases, MCP/tool/skill grants, CredentialRefs and short-lived runtime token references, sandbox policy, and audit requirements into the OpenClaw-derived runtime while keeping raw OpenClaw configuration out of member UX.

## Changed

- Chat readiness and release evidence now lock Matrix/Synapse as the current real provider path, keep non-Matrix chat providers contract-only until promoted, and document Matrix portability/E2EE boundaries.
- Provider registry and release evidence now distinguish `contract_only`, `configured_readiness`, `live_adapter_read`, `live_adapter_write`, `migration_apply_ready`, and `release_ready` providers so contract-only seams cannot appear generally available to members.
- Portability language now uses `portable`, `lossy`, `unsupported`, `manual_review`, `vendor_locked`, and `archive_only` field classes and forbids lossless-migration marketing claims.

## Fixed

- Chat Matrix error handling and member room UI now keep load/send/read-marker failures, encrypted timeline placeholders, unsupported messages, failed sends, and retry states support-safe and accessible.
- Nextcloud Files and Calendar adapters now have stronger release-quality coverage for WebDAV/CalDAV error redaction, invalid path rejection, quota/permission/conflict handling, all-day event preservation, and explicit recurrence blocking until a recurrence contract exists.

## Security

- Product-readiness evidence now records provider-switching, OpenClaw runtime isolation, Weaver tool approval, RBAC, redaction, scan, and support-bundle expectations as explicit release blockers.
- Weaver runtime remains disabled-by-default behind isolation, SecretRef/OAuth broker, signed manifest, egress, audit, and support-bundle redaction contracts.
- Chat-channel projection and Credential Broker implementation for governed Weaver RuntimeProfiles is tracked as Sprint 13 follow-up #519; v0.1.0-rc.3 must not claim that broad runtime execution or raw OpenClaw dashboard/config access is available to members.

## Accessibility

- Sprint 9 release readiness now treats admin setup, provider switching/report review, Calls/LiveKit states, Weaver approvals, and member capability states as release-blocking accessibility flows.
- Sprint 11 now carries a manual assistive-technology evidence template for replacing the Sprint 10 accessibility waiver before v0.1 RC promotion; the template is explicitly not pass evidence until real tester results are recorded.
- Sprint 12 introduces a permanent machine-readable accessibility release gate with expiring issue-linked waivers only.

## Migration/Operator Notes

- Admin Console provider setup now shows domain-first reality level, evidence freshness, restart-survival evidence, and blocks provider apply/switch actions without fresh backend dry-run evidence, consequence confirmation, audit/rollback gates, and provider-neutral member impact preview.
- Self-hosted operations now define provider-aware backup, restore, upgrade, schema migration, support-bundle redaction, observability, and restore-smoke evidence expectations.

## Known Issues

- Nothing yet.
