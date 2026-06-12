---
id: WEAVE-SPEC-0006
title: No-unaccounted-data-loss portability contract
version: 0.1.0
status: implementing
domain: portability
declared_scope: sprint-8-canonical-domains
owner: delivery-owner
github_issue: 429
supersedes: []
depends_on:
  - WEAVE-SPEC-0001
  - WEAVE-SPEC-0004
acceptance_features: []
evidence_gates:
  - ./gradlew specContract
  - ./gradlew portabilityContractCheck
---

# Feature specification: No-unaccounted-data-loss portability contract

## Intent

Define reusable provider replacement schemas so every migration classifies loss, records counts and hashes, links provider mappings and audit references, and blocks apply until a successful dry run exists.

2026-06-12 Northstar amendment: provider portability uses a strict **no unaccounted data loss** policy, not a broad lossless-migration promise. The first provider-switch proof domain is Identity/RBAC, including principal continuity, role/group mapping, token/claim parity, SCIM vs SSO lifecycle behavior, orphan/trust-artifact handling, rollback/archive refs, and post-cutover validation.


## Product boundaries

### In scope

- ProviderAdapterManifest, ProviderMapping, ExportManifest, ImportManifest, LossyMappingReport, ConflictReport, MigrationRun, MigrationAuditRef, and LossClass schemas.
- Dry-run-before-apply validation.
- Support-safe redaction requirements.

### Out of scope

- Live provider mutation.
- Production migration apply.
- Raw provider payload storage in member-visible evidence.

## Functional requirements

- **FR-001**: All fields/objects MUST be classified by a canonical loss class.
- **FR-002**: Migration runs MUST require counts, hashes, provider mapping refs, and audit refs.
- **FR-003**: Apply MUST be impossible without a successful dry-run report.
- **FR-004**: Portability evidence MUST use support-safe redaction.
- **FR-005**: Provider switch preflight MUST identify source provider, target adapter, object/field map, unsupported/lossy/manual-review classes, export/archive refs, rollback path, and timebox.
- **FR-006**: Dry-run evidence MUST include object counts, stable ids/refs, mapping report, consequence preview, no-unaccounted-loss classification, and support-safe evidence refs.
- **FR-007**: Apply/cutover MUST require fresh dry-run evidence, audit sink, rollback/archive refs, receipt counts/hashes/refs/policy/audit refs, and post-cutover validation.
- **FR-008**: Identity/RBAC portability MUST prove principal continuity, group/role mapping, token/claim parity, SCIM lifecycle where available, SSO-staleness limits where applicable, audit trail, rollback function, and orphan/trust-artifact decommission plan.


## Acceptance and evidence mapping

- Tooling test path(s): `tools/portability_contract_check.py`.
- Schemas: `server/src/main/resources/contracts/portability/*.schema.json`.
- Fixtures: `specs/0006-portability-contract/migration-run-*.json`.
- Evidence gates: `./gradlew specContract`, `./gradlew portabilityContractCheck`, `./gradlew docsCheck`.

## Release and migration impact

- Member impact: migration claims remain honest and provider-neutral.
- Admin/operator impact: dry-run, lossy, conflict, and audit evidence become mandatory before apply.
- Developer/API impact: adapters and migration services must conform to shared schemas.
- Data migration/backfill: none in this slice.
- Rollback/reversibility: remove schemas, fixtures, docs, and validation wiring.
- Release-notes label expected: `release-notes-feature`.

## Open questions

None for the schema contract slice.
