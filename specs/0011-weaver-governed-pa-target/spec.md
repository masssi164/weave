---
id: WEAVE-SPEC-0011
title: Weaver governed per-user personal assistant target
version: 0.1.0
status: accepted
domain: weaver-governed-pa
owner: weave-product-lead
github_issue: 719
supersedes: []
depends_on:
  - WEAVE-SPEC-0007
  - WEAVE-SPEC-0009
  - WEAVE-SPEC-0010
acceptance_features: []
evidence_gates:
  - ./gradlew specCorpusConformance
  - ./gradlew specContract
  - ./gradlew acceptanceContract
  - ./gradlew serverCi
---

# Feature specification: Weaver governed per-user personal assistant target

## Intent

Define Weaver as a complete Weave product component: a governed PA for each eligible user, controlled by administrators, isolated per user, policy-bound, auditable, and implemented through Weave domain capabilities. This spec adapts OpenClaw-like product concepts (skills, tools, MCP, memory, approvals, heartbeat/automation) without importing private OpenClaw operator runtime configuration into Weave.

## Product boundaries

### In scope

- Eligibility through organization policy and `weaver-group` membership.
- Admin Control for Weaver enablement, policy, skills/tools, MCP capability scopes, model/provider policy aliases, approvals, heartbeat/automation, and audit posture.
- Per-user agent, memory/context, runtime profile, tool grants, approval receipts, and audit.
- Domain-first tool and MCP boundary with provider adapters behind Weave facades.
- Safe failure: explain, ask when needed, offer fallback, and never mutate silently.

### Out of scope

- Personal/local OpenClaw configuration files, operator paths, private allowlists, local model routing, ACP harness details, and `weave-co-leader` hierarchy.
- Admin default visibility into private per-user memory content.
- Raw provider API/tool exposure to members or Weaver.
- Silent autonomous writes/deletes/external sends/provider changes.

### Non-negotiable constraints

- Weaver is disabled unless org policy and `weaver-group` eligibility allow provisioning.
- Weaver can act only at the intersection of user rights, organization policy, capability/tool scopes, consent, and approval state.
- MCP/tool names and grants are Weave domain-first and support-safe.
- Risky actions require auditable approval receipts and fail closed on stale profile/policy/tool versions.
- Memory is per user and governed by export/delete/retention/privacy rules.

## User/admin/operator stories

### US1 - Admin governs Weaver availability (Priority: P1)

**Actor**: Admin/Owner  
**Story**: Enable Weaver for a governed group, configure allowed capabilities/MCP tools/skills, set approval and automation policy, and inspect audit posture without seeing private member memory by default.  
**Why now**: Weaver cannot be safely implemented before policy and Control ownership are explicit.  
**Independent test**: Admin tests prove group gating, policy previews, tool-scope previews, approval settings, and support-safe audit views.

**Acceptance scenarios**:

1. Given Weaver is disabled for an organization, when an admin adds a member to `weaver-group` without enabling Weaver policy, then no runtime profile is provisioned.
2. Given Weaver policy allows calendar read and board comment with approval, when an admin previews a member's grants, then Control shows domain capabilities, approval requirements, and audit posture without provider secrets.

### US2 - Weaver User receives isolated assistance (Priority: P1)

**Actor**: Weaver User  
**Story**: Use a per-user assistant with personal memory/context that can help across Weave domains while respecting policy, consent, and approvals.  
**Why now**: The complete target product includes PA assistance as a governed member capability.  
**Independent test**: Runtime/profile tests prove per-user isolation, memory separation, grants, redaction, fallback, and audit.

**Acceptance scenarios**:

1. Given two Weaver Users in the same organization, when each asks Weaver to remember personal context, then memory is isolated per user and not visible to the other user or normal admin views.
2. Given a Weaver User asks for a calendar summary, when `calendar.search_events` is granted, then Weaver calls the Weave Calendar facade and receives support-safe results rather than direct provider metadata.
3. Given a Weaver User asks Weaver to send a message externally, when approval is required, then Weaver asks for approval, records the receipt, and invokes only after the receipt matches the current profile and policy.

### US3 - Operator/support audits safe behavior (Priority: P1)

**Actor**: Support/Auditor  
**Story**: Review Weaver actions, blocked policy events, profile hashes, approval receipts, and support-safe diagnostics without exposing secrets or private content beyond role grants.  
**Why now**: Autonomous assistance requires auditability and supportability as first-order product requirements.  
**Independent test**: Audit/evidence tests prove blocked events, redaction, profile hashes, and approval receipts.

**Acceptance scenarios**:

1. Given Weaver blocks an over-scoped MCP invocation, when a support/auditor reviews the event, then the audit record shows user, domain, tool, policy decision, profile hash, and reason with secrets redacted.
2. Given a profile or tool contract changed after approval, when Weaver tries to reuse an old approval, then execution fails closed and records a stale-approval audit event.

## Functional requirements

- **FR-001**: Weaver MUST provision a runtime only for users allowed by organization policy and `weaver-group` membership.
- **FR-002**: Weaver MUST generate a signed per-user `WeaverRuntimeProfile` from Weave policy, user rights, capability grants, model aliases, MCP/tool/skill scopes, credential references, audit policy, and revocation metadata.
- **FR-003**: Weaver MUST keep per-user memory/context isolated and governed by export, delete, retention, and privacy rules.
- **FR-004**: Weaver MUST expose tools through Weave domain capabilities and MCP contracts, not provider APIs or adapter names.
- **FR-005**: Weaver MUST enforce least privilege using the intersection of RBAC, org policy, user consent, tool scopes, approval state, and runtime profile version/hash.
- **FR-006**: Weaver MUST require approval receipts for write, delete, external-send, provider-change, administrative, or otherwise risky actions.
- **FR-007**: Weaver MUST emit support-safe audit metadata for model, channel, memory, MCP/tool, provider facade, approval, denial, and heartbeat/automation events.
- **FR-008**: Weaver MUST support heartbeat/automation only within configured schedules, scopes, rate limits, and approval requirements.
- **FR-009**: Weaver MUST explain failures, ask only when needed, offer safe fallback, and never silently mutate Weave data.
- **FR-010**: Product artifacts MUST NOT contain local OpenClaw operator secrets, personal paths, raw `openclaw.json`, private allowlists, model routing, or `weave-co-leader` as product vocabulary.

## Domain model and contracts

- Canonical Weave entities affected: WeaverUserEligibility, WeaverRuntimeProfile, WeaverMemoryScope, WeaverSkillGrant, WeaverToolGrant, McpCapabilityScope, ApprovalReceipt, AutomationHeartbeat, PolicyDecision, AuditEvent.
- Provider/category contracts affected: model providers, MCP servers, Weave domain facades, credential broker, audit/evidence store.
- API/event contracts affected: runtime profile projection, tool discovery/execution, approval receipt validation, memory export/delete, heartbeat policy, audit events.
- Policy/RBAC/capability keys affected: `weaver.enabled`, `weaver-group`, `weaver.memory.read/write/delete/export`, domain tool scopes, approval modes, automation scopes.
- Audit/support evidence affected: profile hashes, tool contract versions, policy decisions, denial reasons, receipt ids, support-safe parameter summaries.

## Acceptance and evidence mapping

- Gherkin feature path(s): target examples to be split under `e2e/features/weave_spec_0011_acceptance.feature` and existing Weaver features.
- `e2e/scenario_mappings.json` marker(s): add `weave_spec_0011_*` markers before implementation claim.
- Unit/widget/backend/admin/contract test path(s): `server` Weaver runtime/profile/tool tests, `admin-console` policy preview tests, `client` Weaver UX/accessibility tests.
- Live Stack E2E required? Yes before claiming live Weaver availability with Keycloak group and MCP execution.
- Support-safe evidence artifact(s): runtime profile fixtures, approval receipt fixtures, denied-tool events, redacted audit reports.

## Release and migration impact

- Member impact: only eligible members see Weaver; normal members are unaffected.
- Admin/operator impact: Control gains governance, preview, readiness, audit, and remediation obligations.
- Developer/API impact: Weaver must use Weave facades/contracts and credential references.
- Data migration/backfill: memory export/delete/retention and profile revocation must be handled before live claims.
- Rollback/reversibility: disable policy, revoke profile/grants/receipts, and stop automation safely.
- Release-notes label expected: `release-notes-feature` for implementation PRs; `release-notes-skip` for pure spec projection PRs.

## Open questions

- None blocking for target specification and initial implementation slicing.
