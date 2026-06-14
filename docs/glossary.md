# Weave glossary

Status: canonical terminology SSOT for public and implementation-facing Weave docs.

| Term | Meaning |
| --- | --- |
| Weave | Provider-neutral organization collaboration suite: app, server, admin console, infra, e2e, docs, and release evidence. |
| Weaver | Optional later per-user personal-assistant runtime governed by Weave policy. Weaver uses the Weave channel and Weave-owned domain tools/facades. |
| Weave domain | Stable product contract such as chat, files, calendar, boards, calls, decisions, health, or weaver. Domains are not providers. |
| Adapter | Provider implementation behind one or more canonical domains. Adapter evidence owns provider posture, caveats, and replacement path. |
| Provider | External, self-hosted, managed, or Weave-owned service that stores or processes domain data through an adapter. |
| Provider reality level | Evidence level: `contract_only`, `configured`, `live_read`, `live_write`, `migration_dry_run`, `migration_apply_ready`, `rollback_ready`, `release_ready`. |
| Sovereignty posture | Adapter/provider implementation evidence about hosting, operator, residency/jurisdiction exposure, subprocessors, export limits, and lock-in risk. It is not a blanket Weave claim. |
| No unaccounted data loss | Portability promise that unsupported fields, lossy mappings, conflicts, archive-only records, retention limits, and rollback limits are reported before promotion. It is not a perfect-lossless claim. |
| Weave channel | Stable channel surface used by Weaver to interact through Weave chat/domain facades even when the backing chat adapter changes. |
| Weave-owned domain tool | MCP/domain tool exposed through Weave facades and policy rather than raw provider APIs. |
| ApprovalReceipt | Support-safe, audit-linked receipt required before write-like domain-tool actions, deletes, external sends, provider switches, migration actions, or future elevated capabilities. |
| OpenClaw | Candidate governed runtime/harness adapter for Weaver backend execution; not product truth or policy truth. |
| Member state | Stable member-visible capability state: `available`, `disabled_by_policy`, `not_configured`, `degraded`, `unavailable`, or `coming_later`. |
| Admin readiness state | Support-safe setup/readiness state used by admins/operators, including `provider_not_configured`, `secret_missing`, `ready`, `degraded`, `dry_run_required`, `lossy_mapping_pending`, `apply_blocked`, and `migration_ready`. |
| Support-safe | Redacted and actionable: no secrets, tenant URLs, bearer tokens, raw downstream bodies, private prompts, member private content, or raw runtime settings. |
