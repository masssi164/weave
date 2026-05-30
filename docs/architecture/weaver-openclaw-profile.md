# Weaver OpenClaw-derived runtime profile

Weaver is the optional personal-assistant product line inside Weave. Sprint 8 defines the foundation contract only: a per-user, OpenClaw-derived, governed runtime profile generated from organization policy, user consent, capability whitelists, sandbox rules, and audit requirements. Runtime execution is blocked until those controls exist.

## Positioning

- Weave remains the provider-neutral organization suite first.
- Admin Console, identity, RBAC, readiness, whitelisting, diagnostics, backup/restore, and audit come before Weaver runtime execution.
- Weaver profiles are derived from Weave organization policy and user rights. They do not bypass product permissions or provider facades.
- No alternative Weaver runtime is approved by this foundation document.

## Runtime profile contract

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
