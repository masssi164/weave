---
id: WEAVE-SPEC-0005
title: Spaces as cross-domain organization anchors
version: 0.1.0
status: implementing
domain: spaces
declared_scope: sprint-8-canonical-domains
owner: delivery-owner
github_issue: 428
supersedes: []
depends_on:
- WEAVE-SPEC-0001
- WEAVE-SPEC-0004
acceptance_features:
- e2e/features/weave_spec_0005_acceptance.feature
evidence_gates:
- ./gradlew specContract
- ./gradlew spaceAnchorCheck
---

# Feature specification: Spaces as cross-domain anchors

## Intent

Define Space as the provider-neutral organization context that binds chat, files, boards, calendar, calls, decisions, and future Weaver context without exposing raw provider identifiers to members.

## Product boundaries

### In scope

- Space, SpaceType, SpaceMembership, SpaceRole, DomainBinding, ContextPolicy, DefaultSurface, and ContextArchive vocabulary.
- Domain bindings with readiness, source-of-truth, lossy notes, and migration status.
- A minimal fixture binding one Space to chat, files, boards, and calendar.
- Future Weaver context references through Weave policy and facades only.

### Out of scope

- Live provider provisioning.
- Raw provider object IDs in member payloads.
- Weaver direct provider access.

## Functional requirements

- **FR-001**: A Space MUST have stable Weave-owned identity independent of provider containers.
- **FR-002**: Membership and context roles MUST be provider-neutral.
- **FR-003**: Bindings MUST record readiness, source-of-truth, lossy notes, and migration status.
- **FR-004**: Member-safe references MUST not expose raw provider details.
- **FR-005**: Weaver MAY reference a Space only through Weave-governed tools and policy.
- **FR-006**: Northstar workspace claims MUST use Space as the Weave-owned cross-domain context anchor for chat, files, boards, calendar, decisions, and governed Weaver context.
- **FR-007**: Space domain bindings MUST expose readiness, source of truth, migration state, and lossy notes while hiding raw provider object identifiers from member-facing payloads and evidence.

## Acceptance and evidence mapping

- Gherkin feature path(s): `e2e/features/northstar_spec_decisions.feature`.
- `e2e/scenario_mappings.json` marker(s): `NORTHSTAR_SPACE_ANCHOR_CONTEXT`.
- Tooling test path(s): `tools/space_anchor_check.py`.
- Fixture: `specs/0005-spaces-anchor/space-anchor-fixture.json`.
- Evidence gates: `./gradlew specContract`, `./gradlew spaceAnchorCheck`, `./gradlew docsCheck`.

## Release and migration impact

- Member impact: stable workspace context survives provider replacement.
- Admin/operator impact: binding readiness and migration state become explicit.
- Developer/API impact: domain facades can use Space as the context anchor.
- Data migration/backfill: none in this slice.
- Rollback/reversibility: remove fixture, docs, and validation wiring.
- Release-notes label expected: `release-notes-feature`.

## Open questions

None for the anchor contract slice.
