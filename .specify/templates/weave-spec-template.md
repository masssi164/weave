---
id: WEAVE-SPEC-0000
title: Replace with concise product contract title
version: 0.1.0
status: draft
domain: product-core
owner: weave-co-leader
github_issue: null
supersedes: []
depends_on: []
acceptance_features: []
evidence_gates:
  - ./gradlew specContract
  - ./gradlew acceptanceContract
---

# Feature specification: Replace with title

## Intent

State the product outcome in Weave vocabulary. Describe what changes for members, admins/operators, developers, or release owners. Avoid implementation choices until the plan.

## Product boundaries

### In scope

- ...

### Out of scope

- ...

### Non-negotiable constraints

- Weave remains product-first and provider-neutral.
- Normal members must not configure raw providers or see provider secrets/diagnostics.
- Accessibility, supportability, auditability, and deployability are release blockers.
- Weaver remains disabled by default unless this spec explicitly and safely changes a governed placeholder/runtime contract.

## User/admin/operator stories

### US1 - Title (Priority: P1)

**Actor**: Member | Admin | Operator | Developer | Release owner  
**Story**: ...  
**Why now**: ...  
**Independent test**: ...

**Acceptance scenarios**:

1. Given ..., when ..., then ...

## Functional requirements

- **FR-001**: Weave MUST ...
- **FR-002**: Weave MUST NOT ...

Use `[NEEDS CLARIFICATION: question]` only while `status` is `draft` or `proposed`. Accepted or implementing specs must resolve every marker.

## Domain model and contracts

- Canonical Weave entities affected:
- Provider/category contracts affected:
- API/event contracts affected:
- Policy/RBAC/capability keys affected:
- Audit/support evidence affected:

## Acceptance and evidence mapping

- Gherkin feature path(s):
- `e2e/scenario_mappings.json` marker(s):
- Unit/widget/backend/admin/contract test path(s):
- Live Stack E2E required? yes/no + reason:
- Support-safe evidence artifact(s):

## Release and migration impact

- Member impact:
- Admin/operator impact:
- Developer/API impact:
- Data migration/backfill:
- Rollback/reversibility:
- Release-notes label expected: `release-notes-feature` | `release-notes-bugfix` | `release-notes-skip`

## Open questions

- [ ] ...
