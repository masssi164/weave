# Weave product architecture

Status: canonical implementation-facing product architecture SSOT for the Weave monorepo.

## Why Weave exists

Organizations already live across identity providers, chat systems, files, calendars, task tools, meeting systems, evidence stores, and emerging assistant runtimes. A fixed-stack collaboration app becomes another silo. Weave instead gives the organization a stable product layer: members use Weave domains, while admins govern the provider adapters and evidence behind those domains.

The promise is adapter exchange with visible risk, not blanket a universal compliance shield or perfect sovereignty. Data-sovereignty posture is recorded per adapter/provider implementation: hosting model, operator, jurisdiction and residency posture, subprocessors where known, export limits, lock-in risk, evidence, and caveats.

## For whom

- **Members and guests** need coherent collaboration surfaces without raw provider setup.
- **Organization admins** need provider choice, policy, readiness, migrations, approvals, and support-safe diagnostics.
- **Operators** need reproducible setup, redacted support bundles, backup/restore evidence, and release gates.
- **Developers** need canonical domains and facades so client, server, admin console, infra, e2e, and docs do not drift.

## Main components

| Component | Responsibility | Non-goal |
| --- | --- | --- |
| Weave App | Member/guest UX for Spaces, chat, files, documents, calendar, boards/tasks, calls, decisions, notifications, profile, settings, and capability states. | Raw provider setup, secrets, migration plans, or diagnostics. |
| Weave Server | Domain facades, authorization, provider registry, readiness, audit, support-safe errors, portability reports, and migration boundaries. | Letting provider SDK objects define product meaning. |
| Admin Console | Organization setup, adapter selection, RBAC/policy, readiness, risk posture, approvals, and diagnostics. | Member-facing vendor configuration. |
| `weavectl` and infra | Bootstrap, attach-existing/hybrid flows, support bundles, validation, backup/restore evidence, and operator automation. | Unapproved live mutation or secrets in evidence. |
| Provider adapters | Implementation of canonical domain contracts for a provider or composite provider lane. | Product truth or blanket sovereignty status. |
| Weaver | Optional later per-user assistant runtime generated from Weave policy. | A second policy plane or default autonomous writer. |

## Domain model

Canonical domains are product contracts. Current registry keys include `identity`, `people`, `spaces`, `chat`, `files`, `documents`, `calendar`, `boards`, `calls`, `decisions`, `notifications`, `health`, and `weaver`. The registry fixture and docs define canonical objects, capabilities, states, provider reality levels, portability requirements, and adapter manifest requirements.

Domains do not own sovereignty status. Each adapter/provider implementation owns its sovereignty/data-sovereignty posture and evidence.

## Adapter exchange model

Adapters declare domain keys, canonical objects, capability keys, readiness checks, unsupported fields, migration limits, audit events, secret boundaries, source-of-truth mode, provider/jurisdiction posture, evidence links, caveats, and replacement path.

Adapter exchange is promoted in steps only when evidence exists:

1. `contract_only`
2. `configured`
3. `live_read`
4. `live_write`
5. `migration_dry_run`
6. `migration_apply_ready`
7. `rollback_ready`
8. `release_ready`

No member-facing claim may treat a lower level as customer-ready. No migration claim may imply perfect lossless migration; the portability promise is no unaccounted data loss through explicit unsupported-field, conflict, lossy-field, archive-only, retention, and rollback reports.

## Weave channel and Weaver

Weave and Weaver are distinct. Weave is the organization collaboration suite and domain/facade source of truth. Weaver is an optional governed per-user personal-assistant runtime. Weaver uses the Weave channel and Weave-owned domain tools/facades.

OpenClaw is a possible governed runtime/harness adapter for Weaver backend execution. It is not product truth, policy truth, or a member-managed configuration plane. Runtime profiles, tool grants, model aliases, channel bindings, and secrets are generated or brokered from Weave policy.

Approval/read-write semantics belong to MCP/domain-tool actions. Read tools may run within the user's granted capabilities. Write-like actions, deletes, external sends, provider switches, migration actions, and future elevated capabilities require an `ApprovalReceipt` and audit before invocation. These concepts do not redefine domains or adapters and should not be marketed from the README.

## Evidence and readiness

Evidence lives in specs, domain registry fixtures, e2e mappings, release manifests, docs gates, claim-control tests, and sanitized support bundles. Readiness surfaces must be support-safe: no secrets, tenant URLs, bearer tokens, raw downstream bodies, private prompts, member private content, or raw runtime settings.

## Integration path

1. Add or update the canonical domain contract and registry entry.
2. Define adapter manifest fields and source-of-truth behavior.
3. Add readiness, policy, audit, redaction, and support-safe error mapping.
4. Add portability reports and migration/replacement boundaries where applicable.
5. Map product-language acceptance scenarios to executable evidence.
6. Promote claim wording only after gates and evidence support the provider reality level.

## Current architecture references

- [Canonical terminology](glossary.md)
- [Canonical domain registry](domain-registry-v1.md)
- [Canonical domains](architecture/canonical-domains.md)
- [Provider replacement and anti-silo contract](provider-replacement-and-anti-silo-contract.md)
- [Governed Weaver runtime security contract](governed-weaver-runtime-security-contract.md)
- [Contract index](contracts-index.md)
