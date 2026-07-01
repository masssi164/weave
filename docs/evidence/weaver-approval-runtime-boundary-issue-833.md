# Weaver runtime approval boundary evidence (#833)

Status: Sprint 32 Beta-equivalent boundary proof.

## Boundary

- **User OpenClaw/Weaver runtime** owns human approval UX for approval-required tool actions. Native OpenClaw-style runtime semantics are represented as allow-once, deny, and timeout/closed outcomes in the user runtime context.
- **Weave server** owns policy, capability grants, risk class, support-safe receipt metadata, audit correlation, and fail-closed validation. It does not present a server-owned approval dialog and does not decide user approval.
- **MCP tool invocation** is a governed projection over Weave domain tools. The MCP layer carries support-safe approval receipt references and parameter summaries only; it must not expose provider payloads, secrets, raw OpenClaw config, or credential-bearing data.

## Proof slice

`server/src/test/java/com/massimotter/weave/backend/weaver/WeaverToolRegistryTest.java` proves:

- `boards.comment` is a write-like Weaver action and returns `approval_required` with `approvalAuthority=user_openclaw_runtime`, `approvalSurface=openclaw_native_or_beta_equivalent_runtime_ux`, and `serverApprovalDecision=false` until a valid runtime receipt exists.
- A valid runtime approval receipt permits invocation through the Weave domain capability boundary and records only support-safe audit refs.
- Runtime deny and timeout markers fail closed as `approval_denied` and `approval_timeout` without provider access.
- Read-only `calendar.search_events` invokes without approval to avoid approval fatigue.
- Audit/evidence excludes raw provider payloads, secrets, bearer tokens, private prompts, and credential values.

## Claim boundary

This is not a server approval oracle and not a broad always-allow model. Persistent approval, if added later, must be scoped, revokable, expiring where appropriate, and tied to current profile/policy/tool-contract versions.
