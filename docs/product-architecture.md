# Weave product architecture

This is the conceptual source of truth for Weave's product architecture.

## Purpose

Weave exists to give organizations provider-neutral collaboration plus data-sovereignty and operational control when legal, contractual, operational, cost, security, or governance requirements change.

Weave enables adapter/provider exchange, readiness evidence, governance, auditability, and migration paths. It does not guarantee legal immunity, certified compliance, perfect sovereignty, leak prevention, or lossless migration.

## Audience

- Members use Weave collaboration capabilities without learning provider topology.
- Admins choose and monitor provider posture per canonical domain.
- Operators deploy, attach, migrate, back up, restore, and gather support-safe evidence.
- Release owners verify that public claims match implementation and evidence.

## Core model

1. **Canonical domains are product semantics.** They name Weave-owned collaboration surfaces such as identity, files, chat, calendar, tasks, search, audit, and admin/control-room.
2. **Adapters/providers implement domains.** An adapter entry records provider/service identity, authoritative provider links, sovereignty posture, implementation state, jurisdiction/provider posture, hosting/control model, evidence/readiness, caveats, and migration/replacement path.
3. **MCP/domain-tool actions own action semantics.** Read, write, send, delete, migration, provider-switch, approval, audit, and evidence behavior belongs to registered tool actions, not domains and not adapters.
4. **ApprovalReceipt belongs to actions.** Risky MCP/domain-tool actions fail closed unless a valid ApprovalReceipt or explicit governed policy covers the exact action scope.
5. **Weave and Weaver are distinct.** Weave is the product suite and governance layer. Weaver is the governed AI assistant product line inside Weave.
6. **Weaver is OpenClaw-derived but Weave-governed.** A Weaver runtime is a per-user OpenClaw-derived clone/harness profile generated from Weave policy. Users reach it through the Weave channel; it acts through Weave-provided MCP/domain tools, which route to domains, adapters, providers, approval policy, and audit.
7. **The harness is embedded by product boundaries.** OpenClaw runtime concepts/capabilities are central to Weaver execution, but raw OpenClaw configuration, direct provider APIs, raw adapters, databases, unrestricted tools, and member-edited runtime dashboards must not bypass Weave policy, channel identity, domain tools, or registries.

## Architecture layers

| Layer | Owns | Does not own |
| --- | --- | --- |
| Weave channel | Provider-neutral conversation/session/tool/action state and audit refs for Weaver interaction | Raw chat-provider truth or direct runtime authority |
| Canonical domain | Stable product vocabulary and object semantics | Read/write authority, provider claims, approval policy |
| Adapter/provider | Implementation binding, posture, caveats, evidence, migration path | Product semantics or action permissions |
| MCP/domain-tool action | Operation kind, risk, ApprovalReceipt policy, audit/evidence, payload boundary, Weaver-visible tool authority | Provider sovereignty posture |
| Admin/control room | Posture, readiness, policy preview, diagnostics, next safe action | Member-facing raw provider errors/secrets |
| Weaver runtime profile | Per-user OpenClaw-derived harness projection, channel projection, allowed MCP tools, sandbox, approvals, model aliases, revocation/audit | Product semantics, raw provider authority, or member-editable OpenClaw config |
| Evidence/release gates | Claim support, drift checks, readiness artifacts | Unverified legal/compliance guarantees |

## Integration pattern

1. Pick the canonical domain.
2. Read the adapter/provider registry for current or planned implementation posture.
3. Read the MCP/domain-tool action registry for permitted actions, risk, approvals, and audit evidence.
4. If Weaver is involved, generate a per-user runtime profile that exposes only the Weave channel and allowed Weave MCP/domain tools.
5. Expose member UX through Weave semantics.
6. Expose admin/operator posture with support-safe evidence and caveats.
7. Fail closed when registry entries, provider/source links, approval policy, runtime grants, or evidence are missing.

## Advantages

- Provider exchange without silently changing product meaning.
- Adapter-scoped sovereignty evidence instead of broad platform slogans.
- Governed automation with action-specific approvals and auditability.
- Migration planning that surfaces lossy fields, unsupported records, rollback limits, and replacement caveats.
- Public claims constrained to checked-in evidence and sourced legal-risk context.

## Canonical companions

- [Canonical-domain adapter/provider registry](architecture/canonical-domain-adapter-registry.md)
- [MCP/domain-tool action registry](architecture/mcp-domain-tool-action-registry.md)
- [Contract and docs index](contract-docs-index.md)
- [Glossary](glossary.md)
- [Jurisdiction and legal-risk context](jurisdiction-legal-risk-note.md)
