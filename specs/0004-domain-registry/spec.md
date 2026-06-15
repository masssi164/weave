---
id: WEAVE-SPEC-0004
title: Canonical domain registry and portability primitives
version: 0.1.0
status: implementing
domain: domain-registry
declared_scope: sprint-8-9-domain-portability-contracts
owner: delivery-owner
github_issue: 427
supersedes: []
depends_on:
  - WEAVE-SPEC-0001
acceptance_features: []
evidence_gates:
  - ./gradlew specContract
  - ./gradlew serverCi
---

# Feature specification: Canonical domain registry v1

## Intent

Weave needs a stable, provider-neutral registry of product domains so server, admin, client, and release evidence can refer to the same domain keys, member states, admin states, provider reality levels, capabilities, aliases, and portability artifacts without exposing raw provider plumbing to members.

## Product boundaries

### In scope

- Canonical domains: identity, people, spaces, chat, files, documents, calendar, boards, calls, decisions, notifications, health, and weaver.
- Compatibility aliases for historical provider/category names.
- Member-safe and admin/operator states.
- Provider reality levels for every provider candidate so contract-only seams cannot be marketed as generally available.
- Portability schema names for adapter manifests, provider mappings, export/import manifests, lossy mapping reports, conflict reports, and migration runs.
- Deterministic validation that every domain declares portability metadata.

### Out of scope

- Live provider mutation.
- Raw provider diagnostics in member-facing contracts.
- Production migration apply without dry-run, RBAC, audit, rollback/archive, and redaction evidence.
- Enabling Weaver runtime execution.

## Functional requirements

- **FR-001**: The registry MUST be machine-readable and versioned.
- **FR-002**: Every canonical domain MUST declare member states, admin states, capabilities, portability metadata, and support-safe evidence requirements.
- **FR-003**: Compatibility aliases MUST NOT duplicate canonical domain keys or point to more than one domain.
- **FR-004**: Member-facing states MUST remain provider-neutral and MUST NOT require raw provider names, URLs, tokens, or downstream error payloads.
- **FR-005**: Portability artifacts MUST include no-unaccounted-data-loss loss classes and migration run lifecycle states.
- **FR-006**: The validation gate MUST fail when required domains, states, aliases, or portability schemas are missing.
- **FR-007**: Every provider candidate MUST declare exactly one of `contract_only`, `configured`, `live_read`, `live_write`, `migration_dry_run`, `migration_apply_ready`, `rollback_ready`, or `release_ready`; `contract_only` candidates MUST NOT produce member state `available` and only `release_ready` may be described as customer-ready.
- **FR-008**: The registry MUST define canonical binding statuses `active`, `candidate`, `discovery_read_only`, `migration_source`, `migration_target`, `coexistence_preflight`, `deprecated`, and `superseded`, and MUST require exactly one `active` binding per product domain.
- **FR-009**: The registry MUST require AdapterMapper artifacts for every domain: provider-object, capability, permission, event, and error mapping to canonical contracts; provenance, loss, permission-impact, and conflict reports; portability manifests; and support-safe audit refs.
- **FR-010**: Registry-backed member/client contracts MUST reject provider-named capability states or member-visible adapter setup leakage; provider choice remains admin/operator evidence.

## Acceptance and evidence mapping

- Tooling test path(s): `tools/domain_registry_check.py`.
- Registry artifacts: `server/src/main/resources/canonical-domain-registry-v1.json` and `specs/0004-domain-registry/canonical-domain-registry-v1.json`.
- Live Stack E2E required? no; registry contract only.
- Evidence gates: `./gradlew specContract`, `./gradlew serverCi` when server consumers are added.

## Release and migration impact

- Member impact: member-facing capability states can rely on stable domain vocabulary.
- Admin/operator impact: admin readiness and migration tooling can rely on shared states and schema names.
- Developer/API impact: adapter work must declare supported canonical domains, provider reality level, and portability metadata.
- Data migration/backfill: none in this slice.
- Rollback/reversibility: remove registry files and Gradle validation wiring.
- Release-notes label expected: `release-notes-feature`.

## Open questions

None for the registry contract slice.
