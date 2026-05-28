---
id: WEAVE-SPEC-0001
title: Organization embedding and provider-neutral product core
version: 0.1.0
status: draft
domain: product-core
owner: weave-co-leader
github_issue: 381
supersedes: []
depends_on:
  - WEAVE-SPEC-0000
acceptance_features: []
evidence_gates:
  - ./gradlew specContract
  - ./gradlew acceptanceContract
---

# Feature specification: Organization embedding and provider-neutral product core

## Intent

Define Weave's first real product-core slice after the spec framework: how an organization is embedded into Weave through provider-neutral identity, policy, readiness, and capability-state contracts.

This draft intentionally does **not** choose product-core behavior beyond the currently documented direction. It captures the recommended first slice and the decisions Massimo/team must make before implementation.

## Product boundaries

### In scope

- Organization/tenant embedding vocabulary and non-human identity boundaries.
- IDM/RBAC/capability-policy relationship at product level.
- Provider-category readiness states for member/admin/operator surfaces.
- Stable member-facing Weave capability states that avoid raw provider configuration.
- Admin/operator support-safe evidence for missing/degraded providers.
- Weaver as a governed, disabled-by-default capability category unless explicitly promoted by a later decision.

### Out of scope

- Implementing a concrete Chat facade, Files facade, Boards facade, or Weaver runtime.
- Selecting one provider stack as the product model.
- Exposing raw provider secrets, endpoints, diagnostics, or setup flows to normal members.
- Changing live infrastructure or production environments.

### Non-negotiable constraints

- Weave remains product-first and provider-neutral.
- Normal members must not configure raw providers or see provider secrets/diagnostics.
- Accessibility, supportability, auditability, and deployability are release blockers.
- Weaver remains disabled by default unless this spec explicitly and safely changes a governed placeholder/runtime contract.

## User/admin/operator stories

### US1 - Admin embeds an organization by provider category (Priority: P1)

**Actor**: Organization admin  
**Story**: As an admin, I can define which organization providers back Weave categories such as IDM, chat, files/docs, calendar, boards/tasks, meetings, decisions, health, and Weaver.  
**Why now**: Weave must be a provider-neutral suite before feature facades or Weaver runtime can safely depend on capability policy.  
**Independent test**: [NEEDS CLARIFICATION: confirm whether WEAVE-SPEC-0001 starts with Admin/Provider Control Plane, Member capability states, or both.]

**Acceptance scenarios**:

1. Given an organization has selected providers by category, when an admin reviews readiness, then Weave shows category-level readiness without exposing secrets or raw provider payloads.
2. Given a category is not configured, when a member reaches a dependent surface, then Weave shows a stable capability state rather than raw provider setup.

### US2 - IDM/RBAC policy decides capability profiles (Priority: P1)

**Actor**: Admin/operator  
**Story**: As an admin/operator, I can connect identity roles/groups to Weave capability profiles before member/provider access occurs.  
**Why now**: Provider-neutral capability states and future Weaver permissions must derive from organization policy instead of per-call user prompts.  
**Independent test**: [NEEDS CLARIFICATION: decide whether initial acceptance is product-language only or must include server policy contract tests.]

**Acceptance scenarios**:

1. Given IDM role or group membership changes, when Weave evaluates capabilities, then the resulting member capability state follows the configured policy profile.
2. Given Weaver is disabled or not configured, when capability profiles are generated, then Weaver remains unavailable and auditable rather than implied as shipped runtime.

### US3 - Operator evidence stays support-safe (Priority: P2)

**Actor**: Operator/support reviewer  
**Story**: As an operator, I can collect readiness and diagnostic evidence that proves category state without leaking provider secrets or raw payloads.  
**Why now**: Evidence must be trustworthy enough for CI/release review but safe enough for support artifacts.  
**Independent test**: [NEEDS CLARIFICATION: decide whether this slice includes concrete evidence artifact schema changes.]

**Acceptance scenarios**:

1. Given a provider is degraded, when evidence is exported, then the artifact identifies category/readiness impact and redacts secrets/provider payloads.
2. Given a capability is unavailable, when docs/support output is reviewed, then the reason is actionable for admins/operators and not shown as member-facing provider internals.

## Functional requirements

- **FR-001**: Weave MUST model organization embedding by provider-neutral categories rather than a fixed vendor stack.
- **FR-002**: Weave MUST separate member capability states from admin/operator provider configuration and diagnostics.
- **FR-003**: Weave MUST derive member and Weaver capability profiles from organization policy and IDM/RBAC constraints.
- **FR-004**: Weave MUST keep Weaver disabled by default unless a later accepted spec promotes concrete governed runtime behavior.
- **FR-005**: Weave MUST produce support-safe readiness/evidence language that does not leak secrets or raw provider payloads.
- **FR-006**: Weave MUST NOT implement product behavior while any `[NEEDS CLARIFICATION: ...]` marker remains in this spec.

## Domain model and contracts

- Canonical Weave entities affected: Organization, Workspace, ProviderCategory, CapabilityProfile, ReadinessState, PolicyProfile, AuditEvent, SupportEvidence.
- Provider/category contracts affected: IDM, chat, files/docs, calendar, boards/tasks, meetings, decisions, health, Weaver.
- API/event contracts affected: [NEEDS CLARIFICATION: identify first API/event contract after product-scope decision.]
- Policy/RBAC/capability keys affected: [NEEDS CLARIFICATION: decide minimal v0.1 capability vocabulary.]
- Audit/support evidence affected: readiness summary, provider category state, policy evaluation trace, redacted diagnostics.

## Acceptance and evidence mapping

- Gherkin feature path(s): [NEEDS CLARIFICATION: create new `e2e/features/organization_embedding.feature` or extend existing product acceptance flows?]
- `e2e/scenario_mappings.json` marker(s): [NEEDS CLARIFICATION: choose stable markers after story split.]
- Unit/widget/backend/admin/contract test path(s): [NEEDS CLARIFICATION: depends on selected first implementation surface.]
- Live Stack E2E required? no for draft/spec-only PR; yes once provider/control-plane behavior changes.
- Support-safe evidence artifact(s): [NEEDS CLARIFICATION: decide whether this spec creates or references a readiness/evidence schema.]

## Release and migration impact

- Member impact: future stable capability states; no direct runtime change while draft.
- Admin/operator impact: future provider-category readiness and policy workflows.
- Developer/API impact: future canonical product contracts for provider-neutral categories and capability policy.
- Data migration/backfill: none while draft; [NEEDS CLARIFICATION: assess once concrete model/API changes are chosen.]
- Rollback/reversibility: revert spec-only PR while draft; implementation PRs need area-specific rollback plans.
- Release-notes label expected: `release-notes-skip` for draft/spec-only PR; `release-notes-feature` once product behavior changes.

## Open questions

- [ ] Is Organization embedding / IDM / Policy / Readiness the first `WEAVE-SPEC-0001` slice, or should the first product spec be Chat facade, Identity policy only, Admin/Provider Control Plane, or another foundation?
- [ ] Does `product-core` for this first spec cover Member Client only, Admin/Provider Control Plane only, or the provider-neutral suite contract across Member + Admin + Operator?
- [ ] Should Weaver remain a governed placeholder category here, or should concrete per-user runtime profile generation acceptance be included?
- [ ] Which provider categories are mandatory in v0.1/v0.2, and which may be optional/degraded/provider-not-configured?
- [ ] What is the minimal stable capability vocabulary for the first user-visible release?
