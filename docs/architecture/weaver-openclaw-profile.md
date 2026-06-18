# Weaver OpenClaw-derived runtime profile

Weaver is the optional personal-assistant product line inside Weave. Sprint 8 defines the foundation contract only: a per-user, OpenClaw-derived, governed runtime profile generated from organization policy, user consent, capability whitelists, sandbox rules, and audit requirements. Runtime execution is blocked until those controls exist.

## Positioning

- Weave remains the provider-neutral organization suite first.
- Admin Console, identity, RBAC, readiness, whitelisting, diagnostics, backup/restore, and audit come before Weaver runtime execution.
- Weaver profiles are derived from Weave organization policy and user rights. They do not bypass product permissions or provider facades.
- No alternative Weaver runtime is approved by this foundation document.

## Runtime profile contract

The `WeaverRuntimeProfile` is a support-safe runtime projection consumed by the OpenClaw-derived Weaver runtime, not the hard authorization boundary for governed tool calls. The runtime may render an internal `openclaw.json`, channel/plugin entries, MCP server entries, tool filters, model defaults, and sandbox settings, but those files are generated implementation artifacts and correlation inputs only. Normal members must not edit them or use the raw OpenClaw dashboard/config wizard to bypass Weave policy. Weave MCP is the policy-enforcement point for tool invocation: it validates transport/resource auth, user/workspace policy, current tool scope, explicit consent, ApprovalReceipts where required, and auditability before any provider access.

A Weaver runtime profile must be generated per user and per organization. The profile contains only support-safe, auditable grants.

| Profile section | Requirement |
| --- | --- |
| Identity binding | Weave person, immutable identity subject, organization, Space memberships, and effective capability profile. |
| Tool grants | Explicit Weave domain tools only, grouped by canonical domain and capability key. Unknown tools are denied. |
| Provider boundary | Tools call Weave facades, not raw provider credentials or unredacted provider payloads. |
| Secrets | User/org secrets stay in approved secret storage and are never exported into prompts, logs, or member-visible diagnostics. |
| Sandbox | Isolated filesystem/network/process policy appropriate to the granted tools. Cross-user data access is denied by default. |
| Skill packages | Organization-approved skills/packages with version, provenance, and allowlist metadata. |
| Approval policy | Human approval requirements for external sends, destructive actions, provider writes, group-chat participation, and policy-sensitive operations. |
| Audit | Every tool call, approval decision, denied action, and capability-bound data access emits support-safe audit evidence. |
| Member opt-in | Runtime is disabled until the member opts in where required and the organization enables the capability. |
| Group-chat consent | Assistant participation in shared spaces requires explicit organization policy and conversation-level consent signals. |

Minimum profile fields for the next implementation slice:

- profile version, `runtimeProfileHash`, expiry, support-safe integrity/correlation metadata, and rollback pointer;
- model provider aliases, default, fallback order, and user-selectable alias list from admin policy;
- domain capability catalog including `chat.read`, `chat.send`, `files.read`, `calendar.read`, and `weaver.enabled`;
- stable `channels.weave-chat` projection plus Weave Chat-domain routing metadata; Matrix, Teams, iMessage, Slack, Telegram, or another supported provider are backend providerRefs, not separate member/runtime channel configs;
- MCP server/tool/skill grants from Weave policy only, with personal MCPs routed through Weave approval where allowed;
- tool and sandbox deny policy where `tools.deny` wins globally and `bundle-mcp`, gateway, cron, exec, write, and patch-style tools are disabled unless explicitly granted;
- CredentialRefs and short-lived runtime token references only;
- audit policy requiring `runtimeProfileHash`, user, tool, domain, providerRef, credentialRef when applicable, and policy decision for model/channel/tool/MCP calls.

Admin Chat provider changes are provider migrations, not member adapter switches: Weave checks readiness/migration, binds credentials through the Credential Broker, updates backend Chat-domain routing and providerRefs, generates RuntimeProfile vNext while preserving `channels.weave-chat`, reloads or restarts the stable channel/runtime when needed, and keeps member UX inside Weave.

## Per-user runtime container lifecycle

The infrastructure lifecycle contract is defined in `infra/docs/weaver-runtime-lifecycle.md` and the executable static projection is `infra/weave-workspace/weaver-runtime-lifecycle.contract.json`. One active user/trust boundary maps to one active runtime context/container. The trust boundary is the organization, immutable subject, effective capability profile, current server-side policy/session state, and support-safe `runtimeProfileHash` correlation; another browser session for the same boundary attaches to that context instead of creating a second container.

Each runtime context uses separate `stateDir`, `workspaceDir`, and `agentDir` volumes with opaque support-safe refs, a read-only base filesystem, explicit CPU/memory/process/disk quotas, and no implicit host mounts such as Docker socket, SSH agent, keychain, operator home, or raw OpenClaw dashboard. Default egress is deny. The allowed network targets are internal Weave API, internal Weave MCP Gateway, and explicitly allowed channel/MCP proxies that enforce the same RuntimeProfile and short-lived runtime token.

Profile reload, restart, rollback, and server-side session or grant revocation are lifecycle operations, not ad-hoc container edits. Grant/model/approval/audit changes may reload when the loader proves they are safe. Image, network, filesystem, memory, workspace, and sandbox changes require restart. Rollback may activate only a compatible previous projection under the current server-side policy floor. Revocation of sessions, runtime tokens, or tool grants stops or narrows the container as applicable, denies new tool calls at Weave MCP, and preserves only support-safe audit evidence.

## Capability rule

The effective rule is: **user-rights, organization-whitelisted capabilities**.

A user must have product permission for the underlying domain object, and the organization must whitelist the corresponding Weaver tool/capability. The narrower result wins. For example, a user who can read a Space board but lacks `boards.update_task` may receive a read-only board summary tool, not a task mutation tool.

## Blocked until evidenced

Weaver runtime execution stays blocked until implementation proves:

- OpenClaw-derived profile generation is deterministic and policy-bound;
- tool grants are least-privilege and domain-facade mediated;
- member opt-in and admin enablement are enforced;
- approval policy handles external, destructive, and shared-space actions;
- secrets never cross into prompts, logs, or diagnostics;
- sandboxing prevents cross-user and cross-organization data bleed;
- audit and support bundles are redacted and reviewer-verifiable.

Until then, normal members should not see a half-built assistant surface. Admin/operator views may show readiness blockers and next actions.
