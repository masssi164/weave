---
id: WEAVE-SPEC-0010
title: Full Weave target product contract
version: 0.1.0
status: accepted
domain: product-core
owner: weave-product-lead
github_issue: 731
supersedes: []
depends_on:
  - WEAVE-SPEC-0000
  - WEAVE-SPEC-0001
  - WEAVE-SPEC-0004
  - WEAVE-SPEC-0005
  - WEAVE-SPEC-0006
  - WEAVE-SPEC-0007
  - WEAVE-SPEC-0009
acceptance_features:
  - e2e/features/weave_spec_0010_acceptance.feature
evidence_gates:
  - ./gradlew specCorpusConformance
  - ./gradlew specContract
  - ./gradlew acceptanceContract
---

# Feature specification: Full Weave target product contract

## Intent

Define the complete target product as an Organization Operating System with data/provider portability and governed personal assistance. This is target-product truth for Weave plus Weaver, not a release/v0.1 claim and not a description of local OpenClaw staff or `weave-co-leader`.

## Product boundaries

### In scope

- Weave product: Client, Server, Control/Admin, product infrastructure, and provider adapters behind Weave-owned domains.
- Weaver product component: governed per-user personal assistant for eligible members inside Weave.
- Personas: Admin/Owner, Operator/IT, Member, Support/Auditor, and Weaver User.
- Core domains: Identity/RBAC, Spaces, Chat, Files/Documents, Calendar/Meetings, Boards/Tasks, Decisions/Evidence, Admin/Provider, and Weaver.
- Workflow groups: Setup/Governance, Space Work, Provider Change, Weaver Assistance, and Evidence/Audit.

### Out of scope

- Local OpenClaw operator runtime, personal paths, private model routing, live allowlists, and `weave-co-leader` as a product concept.
- A provider-specific suite boundary or vendor-shaped member experience.
- Release/v0.1 readiness, public availability, or production publication claims.

### Non-negotiable constraints

- Weave remains product-first and provider-neutral across all member-facing domains.
- Provider adapters are anti-corruption layers behind Weave-owned contracts.
- Export, delete, provenance, migration dry-run, rollback, and no-unaccounted-data-loss behavior must be explicit where relevant.
- Decisions/Evidence is a product domain, not only implementation evidence.
- Acceptance is expressed in Given/When/Then per persona and domain.
- Accessibility, supportability, auditability, privacy, and deployability remain release blockers.

## User/admin/operator stories

### US1 - Govern an organization as a portable operating system (Priority: P1)

**Actor**: Admin/Owner  
**Story**: Configure organization identity, spaces, provider mappings, policies, and evidence baselines once, then manage provider changes through Weave-owned readiness and migration contracts.  
**Why now**: The target product must prevent provider lock-in and split product truth from current implementation slices.  
**Independent test**: Admin acceptance examples prove setup, readiness, provider-change dry-run, approval, rollback, and audit without exposing raw provider secrets to members.

**Acceptance scenarios**:

1. Given an organization with Identity/RBAC and Spaces configured, when an admin maps providers for chat, files, calendar, and boards, then members see stable Weave capabilities and not provider setup mechanics.
2. Given an admin starts a provider change, when preflight detects permission or data-loss impact, then Weave presents dry-run evidence, required approvals, rollback options, and no silent mutation.

### US2 - Work inside a space with coherent context (Priority: P1)

**Actor**: Member  
**Story**: Enter a Space, find context, participate in chat/meetings/docs/tasks, and understand decisions/evidence through consistent Weave objects.  
**Why now**: The target product is an organization workflow system, not disconnected provider links.  
**Independent test**: Space-work examples prove cross-domain references, permissions, provenance, and accessible UX states.

**Acceptance scenarios**:

1. Given a member belongs to a Space, when they open the Space, then chat, files/documents, calendar/meetings, boards/tasks, and decisions/evidence are presented as related Weave objects governed by the Space policy.
2. Given evidence is linked to a decision, when a support/auditor persona reviews it, then Weave shows provenance and audit metadata without raw provider secrets or private member content beyond role grants.

### US3 - Use Weaver as governed assistance (Priority: P1)

**Actor**: Weaver User  
**Story**: If in `weaver-group`, use a per-user PA that can read, reason, and act through approved Weave domain capabilities under policy, consent, approval, audit, and fallback rules.  
**Why now**: Weaver is part of the complete target product but must not leak OpenClaw operator runtime into product specs.  
**Independent test**: Weaver examples prove group eligibility, per-user memory, RBAC/policy intersection, MCP/tool scopes, approvals, heartbeat/automation, audit, and safe fallback.

**Acceptance scenarios**:

1. Given a member is not in `weaver-group`, when they open Weave, then no personal assistant runtime or tool grants are provisioned for them.
2. Given a Weaver User asks Weaver to create or send something externally, when policy marks the action risky, then Weaver requests approval, records a receipt, and fails closed if policy/profile/tool versions changed.
3. Given Weaver cannot complete a task safely, when capability, consent, or provider evidence is missing, then it explains the reason, asks only necessary follow-up questions, and offers a safe fallback.

## Functional requirements

- **FR-001**: Weave MUST define domains and canonical objects in provider-neutral product language.
- **FR-002**: Weave MUST keep member UX separate from admin/operator provider setup, diagnostics, raw endpoints, and secret material.
- **FR-003**: Weave MUST model provider changes with preflight, dry-run evidence, approval, cutover, rollback/recovery, and audit.
- **FR-004**: Weave MUST expose Decisions/Evidence as a first-class domain with provenance, rationale, linked evidence, audit, and exportability.
- **FR-005**: Weave MUST express acceptance per persona/domain in Given/When/Then form before implementation claims completion.
- **FR-006**: Weaver MUST be a governed product component, disabled unless organization policy and `weaver-group` eligibility allow it.
- **FR-007**: Weaver MUST act only through Weave domain capabilities, never raw provider APIs or private OpenClaw runtime configuration.
- **FR-008**: Product specs MUST NOT encode `weave-co-leader`, local OpenClaw paths, personal operator allowlists, private model routing, or ACP harness choices as product concepts.

## Domain model and contracts

- Canonical Weave entities affected: Organization, Space, Subject, Group, Role, Policy, ProviderRef, Capability, DomainObject, Decision, Evidence, WeaverRuntimeProfile, ApprovalReceipt, AuditEvent.
- Provider/category contracts affected: identity, chat, files/documents, calendar/meetings, boards/tasks, decisions/evidence, admin/provider, weaver tools.
- API/event contracts affected: domain facades, provider readiness reports, migration reports, runtime profile projection, approval receipts, audit events.
- Policy/RBAC/capability keys affected: domain grants, admin/provider grants, support/auditor grants, `weaver.enabled`, `weaver-group`, tool/action scopes.
- Audit/support evidence affected: support-safe diagnostics, provenance, policy decisions, migration dry-runs, blocked events, approval receipts.

## Acceptance and evidence mapping

- Gherkin feature path(s): target examples to be split under `e2e/features/weave_spec_0010_acceptance.feature` or domain-specific feature files.
- `e2e/scenario_mappings.json` marker(s): add markers per task slice before implementation completion.
- Unit/widget/backend/admin/contract test path(s): domain-specific per task slice.
- Live Stack E2E required? Yes for provider-change and Weaver runtime claims; not required for this spec-only target contract.
- Support-safe evidence artifact(s): provider readiness/migration reports, decision/evidence export fixtures, Weaver profile/approval/audit fixtures.

## Release and migration impact

- Member impact: stable domain UX and optional governed Weaver where eligible.
- Admin/operator impact: stronger policy, provider, migration, and evidence obligations.
- Developer/API impact: implementation work must trace to domain contracts and Spec Kit tasks.
- Data migration/backfill: domain-specific, requires dry-run/no-loss reports where relevant.
- Rollback/reversibility: required for provider changes and risky admin actions.
- Release-notes label expected: `release-notes-feature` for implementation PRs; `release-notes-skip` for pure spec projection PRs.

## Open questions

- None blocking. Future forms may refine non-blocking product choices without pausing initial implementation slicing.
