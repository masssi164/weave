# Glossary

This glossary defines Weave product language. It intentionally separates product semantics, provider implementation, governed actions, and the OpenClaw-derived Weaver runtime model.

## Product and runtime

- **Weave**: Provider-neutral organization collaboration suite. Weave owns the product semantics, policy boundaries, admin/operator control surfaces, evidence gates, and member-facing collaboration experience.
- **Weaver**: Governed AI assistant product line inside Weave. A Weaver instance is a per-user agent/runtime boundary reached through the Weave channel and governed by Weave policy, user rights, organization allowlists, approvals, sandboxing, and audit.
- **AI harness**: The execution runtime shape used to run an assistant turn. For Weaver, the intended harness is OpenClaw-derived: it can run an agent, receive channel turns, use tools, apply policy, keep sessions, and emit audit/support evidence.
- **OpenClaw clone / OpenClaw-derived Weaver runtime**: A Weaver runtime profile generated from Weave policy and based on OpenClaw runtime concepts/capabilities. It is not a raw member-editable OpenClaw install. Weave generates the profile, channel projection, tool grants, MCP entries, sandbox policy, model aliases, and approval rules.
- **Agent**: The assistant actor inside a runtime. In Weaver, an agent is scoped to a user/organization boundary and receives only the capabilities granted by Weave policy.
- **Session**: Durable conversation/runtime state for an agent interaction. Weave keeps member-facing session semantics on the Weave channel while the underlying harness may keep its own runtime/session metadata.
- **Weave channel / `weave-chat`**: Provider-independent conversation identity for Weaver interaction. It preserves Weave-owned actor, conversation, session, thread/run, message, tool/action, policy, and audit references while backend chat providers remain implementation details.
- **Channel**: Messaging surface through which an agent receives and sends conversation turns. In OpenClaw, channel plugins connect platforms such as Matrix, Slack, Telegram, Discord, or custom transports; in Weave, the stable product channel is `weave-chat`.
- **Plugin**: Extension package that can add channels, model providers, tools, skills, speech, search, media capabilities, or runtime/harness support. For Weave, plugin-like implementation details sit behind governed product boundaries.
- **Tool**: Typed action callable by an agent or harness. Tools are the operational interface for reading, writing, searching, sending, querying, or controlling systems. In Weave, tools exposed to Weaver must be Weave-owned domain tools or approved MCP tools, not raw provider credentials or direct database access.
- **Skill**: Prompt/package guidance that teaches an agent when and how to use tools. Skills can shape behavior, but they do not grant authority; Weave policy and tool grants do.
- **MCP server**: Model Context Protocol server exposed by Weave to a Weaver runtime. It publishes governed domain tools and enforces runtime profile, capability, approval, audit, payload, and provider-boundary rules.

## Product architecture

- **Canonical domain**: Weave-owned product semantics and stable object vocabulary, such as chat, files, calendar, tasks, identity, search, audit, or admin/control-room. Domains describe what a capability means; they do not grant action authority.
- **Adapter**: Implementation binding that maps a canonical domain to a provider/service. An adapter carries implementation state, sovereignty/jurisdiction posture, hosting/control model, caveats, readiness evidence, and migration/replacement path.
- **Provider/service**: External or self-hosted system used behind an adapter, such as a chat, file, calendar, identity, board, storage, search, meeting, or AI service.
- **Provider posture**: Support-safe statement of a provider/adapter’s hosting model, jurisdiction/provider exposure, credential boundary, readiness level, caveats, and evidence.
- **Sovereignty posture**: Adapter/provider-specific statement of organizational control, hosting, jurisdiction/provider exposure, replacement path, caveats, and evidence.
- **Implementation state**: Current maturity of a domain/adapter/provider path, for example contract-only, configured, live-read, live-write, migration-dry-run, migration-apply-ready, rollback-ready, or release-ready.
- **Admin Control Room**: Admin/operator surface for provider posture, readiness, policy preview, support-safe diagnostics, migration/replacement caveats, and next safe actions.
- **Member UX**: Product surface used by members and guests. It should speak Weave capabilities and states, not raw provider setup or operator internals.

## Governed actions

- **MCP/domain tool**: Weave-owned action surface for a canonical domain. It defines operation kind, input/output boundary, risk, payload limits, audit evidence, and approval semantics.
- **Tool action**: A specific operation such as read, search, write, send, delete, migrate, switch provider, create evidence, query audit, or invoke a runtime action.
- **Read action**: Tool action that returns existing data through a governed Weave facade and support-safe payload boundary.
- **Write action**: Tool action that changes state. Writes require explicit policy coverage and, when risk demands it, an ApprovalReceipt.
- **ApprovalRequest**: Proposed action approval prompt with actor, target, intent, risk, diff/payload summary, scope, expiry, and rollback hint.
- **ApprovalReceipt**: Approval artifact for an exact MCP/domain-tool action scope. It authorizes the specific action it covers, not a whole domain, adapter, provider, or future sequence of unrelated actions.
- **Runtime profile**: Signed per-user/per-organization configuration consumed by the Weaver runtime. It includes identity binding, model aliases, channel projection, domain capability grants, allowed MCP tools, sandbox policy, approval policy, CredentialRefs, expiry/revocation state, and audit requirements.
- **Capability grant**: Policy-approved permission for a user/runtime to access a specific Weave domain capability or tool action. Effective access is the narrower result of user rights and organization-whitelisted capabilities.
- **CredentialRef**: Reference to a credential managed by Weave-approved secret storage. It allows runtime/tool routing without exposing raw secrets to prompts, logs, support bundles, or member-visible diagnostics.
- **Support-safe payload**: Redacted payload shape safe for logs, support bundles, docs, and public evidence. It excludes raw secrets, unredacted provider bodies, private prompts, member content, and operator-private paths.
- **Audit evidence**: Support-safe record of policy decisions, tool calls, approvals, denials, provider posture, runtime profile hash, and relevant object references.

## Legal and claims language

- **Data sovereignty**: Weave product direction for organizational control over collaboration semantics, provider exposure, policy, evidence, portability, and migration boundaries. It is expressed through adapter/provider posture and governed actions, not blanket slogans.
- **Jurisdiction exposure**: Sourced risk context about which provider, hosting, legal, contractual, or operational regimes may affect a domain/adapter path.
- **CLOUD Act**: Example legal-risk context for US-provider and jurisdiction exposure. README language should stay abstract; sourced details belong in legal-risk context docs or this glossary.
- **Legal-risk context**: Sourced background about legal regimes or jurisdiction exposure, kept separate from feature claims and implementation evidence.
- **Claim gate**: Check that public/product language matches checked-in evidence, contracts, fixtures, and sourced context.
- **Readiness evidence**: Checked-in or linked proof that a claim is supportable for a given adapter, action, runtime profile, migration path, release state, or provider posture.

## Boundaries

- Domains define product meaning.
- Adapters/providers implement product meaning.
- MCP/domain-tool actions grant and execute operations.
- ApprovalReceipts authorize exact risky actions.
- Weaver acts through the Weave channel and Weave-owned tools.
- OpenClaw-derived runtime capabilities are embedded through Weave policy and product boundaries.
