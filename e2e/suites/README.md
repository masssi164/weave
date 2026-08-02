# Weave E2E suite catalog

This directory contains the professional E2E structure that sits above the raw
Gherkin files and marker mappings.

- `scenario_catalog.json` classifies every mapped scenario by suite, persona,
  bounded domain, test level, execution lane, and assertion focus.
- `tools/e2e_structure_check.py` fails if a Gherkin/mapping scenario is not in
  the catalog, if a suite has no scenarios, if a required domain/persona loses
  coverage, or if a live/offline evidence mode drifts.
- `./gradlew acceptanceContract` runs both the Dart acceptance mapping guard and
  this suite-structure guard.
- `./gradlew e2eStructureCheck` runs only the catalog guard.

## Suite lanes

| Suite | Lane | Purpose |
| --- | --- | --- |
| `member-live-critical-path` | `governed-live-product-evidence` | Automated `testApp` and explicitly separate physical-device runtime evidence without credential-injected Flutter builds. |
| `v0-1-dogfood-release-spine` | `pr-safe-offline-contract` | Deterministic release-scope evidence across member, admin, server, docs, and fixtures. |
| `spec-0001-org-embedding` | `pr-safe-offline-contract` | Pinned-corpus organization embedding acceptance projection. |
| `admin-provider-portability-lifecycle` | `pr-safe-offline-contract` | Admin/provider lifecycle, identity/offboarding, WOPI posture, Weaver preflight, and release operations. |
| `cross-domain-provider-proof` | `provider-proof-fixture-contract` | Calendar/files provider-switch evidence, fixed Keycloak federation readiness, and commercial adapter boundaries. |
| `product-e2e-scenario-layer` | `scenario-first-offline-contract` | Product/business flows that must be specified before runtime implementation expands, including guests, degraded states, documents, support bundles, backup/restore, lifecycle actions, and Weaver consent. |

Do not add decorative scenarios. A new scenario must have a Gherkin tag, a
`scenario_mappings.json` executable evidence mapping, and a catalog entry before
it is reviewable.
