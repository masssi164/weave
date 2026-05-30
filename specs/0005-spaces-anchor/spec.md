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
acceptance_features: []
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

## Acceptance and evidence mapping

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
