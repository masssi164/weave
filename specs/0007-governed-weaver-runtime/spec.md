---
id: WEAVE-SPEC-0007
title: Governed Weaver runtime and tool approval contract
version: 0.1.0
status: implementing
domain: weaver-runtime
owner: weave-security-compliance-lead
github_issue: 433
supersedes: []
depends_on:
  - WEAVE-SPEC-0001
acceptance_features:
  - e2e/features/v0_1_dogfood_release.feature
evidence_gates:
  - ./gradlew specContract
  - ./gradlew acceptanceContract
  - ./gradlew serverCi
---

# Feature specification: Governed Weaver runtime and tool approval contract

## Intent

Define the first implementation-ready Weaver runtime contract without making Weave agent-first. Weaver remains optional, disabled by default, per-user, auditable, and generated from organization policy plus user rights.

## In scope

- OpenClaw-derived per-user runtime profile schema.
- Clear separation between runtime provider, model provider, and tool provider.
- Per-user isolation boundary for workspace, memory, and sessions.
- Domain-scoped Weave tool registry generated from approved capabilities.
- Approval receipts for write/delete/external-send/provider-switch actions.
- Security, privacy, accessibility, and support-safe release evidence requirements.

## Out of scope

- Starting real runtime containers.
- Publishing a production image or release.
- Autonomous Weaver writes without approval receipts.
- Raw provider-token delivery to runtime.
- Admin visibility into member private memory by default.
- Live infrastructure mutation.

## Functional requirements

- **FR-001**: The runtime profile MUST expose runtime provider, model provider, and tool provider as separate concepts.
- **FR-002**: The runtime profile MUST be generated from workspace capability policy and MUST remain disabled by default.
- **FR-003**: The runtime profile MUST use support-safe user references and MUST NOT contain raw provider tokens.
- **FR-004**: The runtime profile MUST express tools as Weave domain capabilities, not provider APIs. MCP tool names MUST be domain-first, such as `calendar.search_events`; adapter/provider-prefixed calendar tool names are forbidden in the governed member runtime.
- **FR-005**: Tool discovery MUST filter by the generated user grants.
- **FR-006**: Unauthorized tool invocation MUST be blocked and audited.
- **FR-007**: Write/delete/external-send/provider-switch actions MUST require approval receipts before invocation.
- **FR-008**: Tool schemas MUST be explicit, versioned, and domain-scoped.
- **FR-009**: Tool results and failures MUST be support-safe and redacted before returning to the runtime.
- **FR-010**: Admin/operator evidence MUST include policy posture and audit metadata, not private member memory content.
- **FR-011**: Release evidence MUST capture OpenClaw fork/image digest/SBOM/scan references before any release claim.
- **FR-012**: Weaver approval UX MUST be screen-reader accessible and must not rely on color-only state.
- **FR-013**: A `WeaverRuntimeProfile` MUST be the only source consumed by the OpenClaw-derived Weaver runtime. The signed profile MUST include version, hash, revocation metadata, model aliases/defaults/fallbacks, domain provider projections, MCP/tool/skill grants, sandbox/tool-deny policy, CredentialRefs, and audit policy.
- **FR-014**: Chat provider changes MUST be modeled as admin-governed Weave Chat domain provider migrations. The profile generator MUST normally expose a single stable OpenClaw channel plugin, `channels.weave-chat`, backed by Weave Chat-domain routing; members MUST NOT switch raw chat adapters, provider-named channel plugins, or channel tokens. The channel plugin path is separate from MCP/domain tools: MCP `chat.send_message` is not a user-to-Weaver messaging channel. Implementation ownership stays split across `masssi164/weaver#20` for the `weave-chat` channel plugin and `#764` for the Weave MCP/domain-tool server.
- **FR-015**: Raw OpenClaw configuration surfaces (`openclaw.json`, `openclaw config`, setup wizard, dashboard controls for gateway/channels/MCP/secrets/sandbox/exec/tool allowlists) MUST be disabled, read-only, or RBAC-stripped for normal members.
- **FR-016**: Credential handling MUST use Weave Credential Broker references and short-lived runtime tokens. Weaver profiles, logs, prompts, support bundles, and release evidence MUST NOT contain provider secrets, OAuth refresh tokens, cookies, or credential-bearing provider URLs.
- **FR-017**: Every model, channel, tool, MCP, and provider call MUST emit support-safe audit metadata containing at least `runtimeProfileHash`, user, tool, domain, providerRef, credentialRef where applicable, and policy decision.
- **FR-018**: `calendar.search_events` MUST delegate to the Weave backend Calendar facade/capability boundary. Provider choice, including any current Nextcloud CalDAV backing, is selected by stack/admin configuration behind that facade and MUST NOT be called directly by the MCP server or exposed in member/runtime discovery metadata.
- **FR-019**: Discovery metadata MUST expose Weave domain, capability, read/write posture, approval requirement, and support-safe schema only. `providerRef`, adapter URL, credentialRef target detail, and raw downstream diagnostics MUST remain hidden or redacted from normal runtime discovery and fail closed on missing backend authority.

## Initial tool set

- `calendar.search_events` read-only.
- `boards.search_tasks` read-only.
- `files.search` read-only.
- `chat.search_messages` read-only or guarded by chat policy.
- `notifications.create_action_request` guarded external-send.
- `boards.comment` write-with-approval.

## RuntimeProfile projection model

The implementation projection for the Weaver/OpenClaw fork is intentionally one-way:

1. Weave remains source of truth for domains, admin policy, provider selection, credentials, and audit.
2. The server profile generator signs and versions a `WeaverRuntimeProfile` from that source of truth.
3. The Weaver RuntimeProfile Loader renders internal `openclaw.json` and related channel/plugin/MCP/tool settings as runtime implementation detail.
4. OpenClaw Policy/Doctor is used as a conformance lint gate over rendered settings, not as a second policy source.

The minimum profile sections are:

| Section | Requirement |
| --- | --- |
| Identity and profile metadata | Organization, user, profile version, `runtimeProfileHash`, expiry, revocation status, and previous-profile rollback pointer. |
| Models | Admin-selected provider/model aliases, default, fallback order, and user-selectable aliases only. |
| Domains and providers | Stable domain grants such as `chat.read`, `chat.send`, `files.read`, `calendar.read`, and `weaver.enabled`, plus providerRef bindings hidden from member UX. |
| Chat channel projection | Stable `channels.weave-chat` configuration with Weave API/runtime token/profile hash metadata. ProviderRefs such as `matrix`, `teams`, `imessage`, or `slack` stay behind Weave Chat-domain routing and are not rendered as separate per-user OpenClaw channel configs in the normal Weaver path. |
| MCP, skills, and tools | Admin-distributed MCP servers/tools/skills only; personal MCPs only through a Weave-approved flow. `tools.deny` remains hard-deny and `bundle-mcp` is disabled unless the profile explicitly permits it. |
| Credentials | CredentialRefs and short-lived runtime token references only; OAuth refresh/runtime credentials stay behind the Weave Credential Broker. |
| Sandbox and runtime lifecycle | One active user/trust boundary per runtime context/container, separate workspace/state/agentDir, internal-only network targets, reload/restart on profile changes, and rollback to the previous signed profile. |
| Audit | Required fields for model/channel/tool/MCP/provider calls and denied decisions. |

Provider-change flow: Admin changes the Weave Chat domain provider -> server readiness and migration checks run -> Credential Broker binds new provider credentials -> Weave backend Chat routing/profile version changes -> `WeaverRuntimeProfile` vNext is generated and signed with the same stable `channels.weave-chat` channel id and updated metadata -> channel/runtime reloads or restarts if needed -> member continues through Weave UX.

Channel/tool separation: Weave owns the Chat product API, policy, audit, RuntimeProfile generation, and Weave MCP server/domain tools. Weaver owns the OpenClaw-derived `weave-chat` ChannelPlugin runtime, OpenClaw session routing, inbound/outbound channel messaging, approval hint rendering, and MCP client binding to the Weave MCP server. OpenClaw documentation treats channel plugins (`api.registerChannel(...)`, shared `message` tool, channel-owned config/security/session grammar/outbound/threading) and agent tools/MCP servers (`api.registerTool(...)`, MCP server definitions consumed by runtimes, tool allow/deny policy) as separate extension points. Runtime evidence must prove the channel roundtrip separately from optional same-turn MCP tool calls.

This flow is tracked for the next implementation slice in issue #519 and is not a new Sprint 12 runtime-execution claim.

## Acceptance mapping

- `@weave-v01-governed-weaver-runtime-policy` proves profile generation, disabled-by-default posture, policy intersection, and audit.
- `@weave-v01-governed-weaver-tool-registry` proves domain-tool discovery, blocking, approval receipts, redaction, and audit.
- `@weave-v01-provider-switch-portability` remains the governing acceptance boundary for provider-switch approval and support-safe evidence.

## Evidence

- `server/src/test/java/com/massimotter/weave/backend/service/WeaverRuntimeServiceTest.java`
- `server/src/test/java/com/massimotter/weave/backend/weaver/WeaverToolRegistryTest.java`
- `docs/governed-weaver-runtime-security-contract.md`
- `docs/evidence/weaver-security-privacy-accessibility-report.md`
