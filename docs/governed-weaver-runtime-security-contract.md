# Governed Weaver runtime security contract

Status: active contract for issues #433 and #446-#449.

Weaver is an optional per-user personal-assistant runtime. It is generated from Weave organization policy and the member's normal rights; it is not a second, uncontrolled agent policy plane.

## Control-plane boundary

- **Runtime provider**: an OpenClaw-derived container image pinned by Weave release evidence.
- **Model provider**: an organization-selected model profile; the runtime receives the profile key, not provider secrets.
- **Tool provider**: the Weave domain tool registry. Tools are generated from approved Weave capabilities only.
- **Policy source**: Admin Console policy plus IDM/RBAC group/user grants plus member opt-in where allowed.
- **Secret posture**: SecretRefs only. Raw provider tokens, downstream payloads, service endpoints, and secret values never appear in the runtime profile, member client, logs, support bundles, screenshots, or release evidence.

## RuntimeProfile and OpenClaw projection boundary

The Weaver/OpenClaw fork consumes one signed `WeaverRuntimeProfile` from Weave. Weave remains the source of truth for domains, provider selection, policy, credentials, and audit; OpenClaw configuration is generated runtime output, not a member-managed product model.

Required projection controls:

- RuntimeProfile Loader renders internal `openclaw.json` from the signed Weave profile.
- Normal members cannot edit `openclaw.json`, run the OpenClaw config wizard, manage gateway/channels/plugins/MCP/secrets/sandbox/exec/tool allowlists, or use raw dashboard controls for those areas.
- Member-facing Weaver settings are limited to policy-allowed model aliases, style, memory/workspace preferences, allowed skills, and allowed personal MCP connection flows exposed by Weave.
- Admin policy projects model defaults, fallbacks, and allowed aliases; users choose only among Weave aliases, not raw provider/model identifiers.
- Weaver normally exposes one stable OpenClaw channel plugin, `channels.weave-chat`. Matrix, Teams, iMessage, Slack, Telegram, and other chat systems remain Weave Chat-domain providers behind Weave server routing, not separate per-user Weaver channel configs.
- MCP servers, skills, and tools are distributed through Weave policy. `tools.deny` is hard-deny; `bundle-mcp`, gateway, cron, exec, write, and patch-style capabilities remain default-deny unless the signed profile explicitly allows a constrained use.
- OpenClaw Policy/Doctor output is conformance lint over generated settings. It is not a second source of truth.

Correct Chat provider-change flow: Admin changes the Chat domain provider in Weave -> readiness/migration checks run -> Credential Broker binds new provider credentials -> Weave backend routing/profile version changes -> signed RuntimeProfile vNext still exposes `channels.weave-chat` with updated profile hash/runtime token metadata -> the stable channel reloads or restarts if needed -> the user continues through Weave UX.

## Disabled-by-default gates

Runtime provisioning is fail-closed unless all gates pass:

1. The Weaver provider category is enabled by organization policy.
2. The governed runtime generator is enabled.
3. The user or group has `weaver.enabled` through IDM/RBAC capability policy.
4. The member has opted in where the policy requires opt-in.
5. The runtime profile intersects user rights with the admin capability/tool allowlist.

If any gate fails, member surfaces show only `disabled_by_policy`, `not_configured`, `degraded`, `unavailable`, or `coming_later` impact states and never expose provider diagnostics.

## Isolation boundary

Each enabled active user/trust boundary receives one isolated runtime boundary, or an implementation-equivalent hard isolation model, with:

- per-user workspace volume;
- per-user memory store;
- per-user session store;
- separate runtime state and agent directory;
- no access to another user's workspace, memory, or sessions;
- no raw provider tokens;
- only Weave API, Weave MCP Gateway, and allowed channel/MCP proxy access;
- short-lived runtime tokens plus CredentialRefs, not stored provider secrets;
- profile reload/restart and rollback to the previous signed profile on admin changes;
- audit-required profile generation and tool invocation.

## Approval policy

Read tools may run within the user's granted capability set. Write-like actions require approval receipts before invocation:

- writes;
- deletes;
- external sends/notifications;
- provider switches or migration actions;
- any future exec/elevated capability.

Routine approved read operation must not require per-call confirmation. High-risk actions require a support-safe `ApprovalReceipt` that records actor, action, scope, policy version, expiry, and audit reference without private memory content.

## Tool registry v1

The initial registry exposes stable, versioned, domain-scoped names only through Weave facades:

| Tool | Mode | Required capability | Approval |
| --- | --- | --- | --- |
| `calendar.search_events` | read | `weaver.calendar_read` | none |
| `boards.search_tasks` | read | `weaver.boards_read` | none |
| `files.search` | read | `weaver.files_read` | none |
| `chat.search_messages` | read | `weaver.chat_read` | none or guarded by chat policy |
| `notifications.create_action_request` | external-send | `weaver.notifications_write` | required |
| `boards.comment` | write | `weaver.boards_write` | required |

Discovery filters by the generated runtime profile's grants. Blocked tools are not discoverable and unauthorized invocation is blocked and audited. Results are redacted before returning to the runtime.

## OpenClaw fork and supply-chain posture

Weave treats OpenClaw as runtime substrate. Weave owns policy, grants, profiles, audit, approval receipts, and domain tools.

Release evidence must capture:

- organization-owned fork URL and pinned upstream commit/tag;
- upstream sync, security patch, and local patch policies;
- image digest for the Weave-owned OpenClaw-derived runtime image;
- SBOM reference;
- dependency and container scan references;
- proof that runtime images contain no baked provider/user secrets.

## Admin/member privacy

Admins can see policy posture, audit metadata, readiness, grants, and approval summaries. They do not see member private memory by default. Private memory export/delete follows the member's rights and domain export/delete policies; support bundles must redact memory content unless an explicit, audited support authorization permits a narrower disclosure.

## Evidence gates

- `./gradlew serverCi` for runtime profile and tool registry contracts.
- `./gradlew acceptanceContract` for mapped product-language scenarios.
- `./gradlew specContract` for the governed Weaver runtime spec (`WEAVE-SPEC-0007`).
- Release hardening evidence in `docs/evidence/weaver-security-privacy-accessibility-report.md` before any release claim.

## Sprint 12 preflight: sandbox, registry, SecretRef, and OAuth contracts

Sprint 12 keeps Weaver runtime execution disabled by default. See `docs/architecture/adr-003-weaver-runtime-isolation.md` for the isolation decision. Docker rootless is not accepted as a strong sandbox by itself; stronger gVisor/runsc or Firecracker evidence is required before broader runtime claims.

### Signed skill/tool manifest

Every admin-distributed skill or tool manifest must be version-pinned and include:

- signature and provenance;
- semantic version and immutable artifact digest;
- declared capabilities, approval class, data classes, and egress destinations;
- SecretRefs and OAuth/service-account broker requirements;
- audit events for install, update, rollback, grant, deny, invoke, and cleanup; and
- support-safe evidence fields for review and release promotion.

Unsigned, unpinned, overbroad, undeclared-egress, raw-secret, or raw-provider-payload manifests are rejected. Install, update, rollback, capability grant, and admin approval receipts are modeled as audit-linked evidence; no marketplace or broad third-party execution is included in this sprint.

### SecretRef and OAuth broker rules

Runtime profiles, logs, support bundles, tool results, and PR/release evidence may contain only stable SecretRef identifiers and support-safe broker receipts. Raw secrets, client credentials, OAuth refresh tokens, cookies, provider URLs with credentials, and downstream payload bodies are forbidden. The broker must scope grants to actor, organization, tool, capability, approval receipt, and expiry.
