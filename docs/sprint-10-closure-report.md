# Sprint 10 Closure Report — Readiness Debt & E2E Contract Recovery

## Governing scope

Sprint 10 closes the readiness debt discovered after the Sprint 8/9 audit. The sprint is governed by GitHub milestone 10 and issues #466–#477, repo-local specs, the acceptance contract mapping, and executable Gradle gates.

## Issue DAG final state

- #467 Domain registry drift gate — prerequisite for readiness claims; fixed by syncing the server resource copy from the canonical spec registry and deepening the registry guard.
- #475 Duplicate spec traceability — prerequisite for spec evidence integrity; fixed by renumbering governed Weaver runtime to WEAVE-SPEC-0007 and adding duplicate global spec-id detection.
- #471 Portability schema family — fixed by using the canonical migration portability schema family and rejecting sibling legacy schema drift.
- #468 Migration apply gates — depends on dry-run evidence; fixed by making apply decisions depend on current server-side evidence rather than request self-attestation.
- #470 Migration API security — fixed with admin/operator scoped access for migration control-plane APIs and negative member tests.
- #474 Admin fresh dry-run UX — fixed in the admin console by requiring fresh dry-run evidence and explicit consequence confirmation before provider apply.
- #469 Live Stack marker contract — fixed by distinguishing live-runtime markers from offline/spec executable evidence markers.
- #472 Product-readiness evidence honesty — fixed by aligning readiness claims with executable evidence and documenting remaining evidence modes.
- #473 Accessibility release gate — fixed with an explicit Sprint 10 manual accessibility waiver path instead of an implied pass.
- #476 Sprint 8/9 audit accuracy — fixed by correcting Sprint 8/9 closure/readiness reports to reflect post-audit blockers.
- #477 Readiness contract hardening — fixed by deepening registry, portability, waiver, and provider-vocabulary checks.
- #466 Program closure — closes after the integrated PR is merged, CI is green, and the milestone is closed.

## Implementation summary

- Restored canonical domain registry drift protection between `specs/0004-domain-registry/canonical-domain-registry-v1.json` and `server/src/main/resources/canonical-domain-registry-v1.json`.
- Added spec-contract duplicate global ID detection and moved governed Weaver runtime from duplicate `WEAVE-SPEC-0004` to `WEAVE-SPEC-0007`.
- Made migration apply gates fail closed unless current server-side dry-run evidence exists for the run/domain, with stale and forged-request negative tests.
- Restricted `/api/migration/**` to workspace scope plus owner/admin/operator roles.
- Required admin-console provider apply to use fresh dry-run evidence and consequence acknowledgement.
- Split Live Stack acceptance markers into `live-runtime` and `offline-spec` evidence modes so scheduled live runs no longer fail on offline/spec markers.
- Added an explicit manual accessibility waiver document for Sprint 10 release evidence.
- Corrected Sprint 8/9 closure/readiness documents so administratively closed milestones are not represented as product-readiness complete.

## Evidence gates

Local gates run before PR:

- `./gradlew specContract domainRegistryCheck portabilityContractCheck --console=plain` — PASS.
- `cd server && ./gradlew cleanTest test --console=plain` — PASS.
- `cd server && ./gradlew test --tests 'com.massimotter.weave.backend.service.migration.*' --tests 'com.massimotter.weave.backend.controller.MigrationControllerSecurityTest' --tests 'com.massimotter.weave.backend.controller.PlatformProductContractControllerTest' --console=plain` — PASS.
- `./gradlew adminCi acceptanceContract docsCheck --console=plain` — PASS.
- `./gradlew infraStatic releaseEvidenceCheck specContractTest --console=plain` — PASS.

- `./gradlew clientCi --console=plain` — PASS after staging.
- PR #478 checks — PASS: Gradle CI runs `26704946251` and `26704947108`; Release Notes Label Check run `26704947108`.

## Live Stack E2E status

The Sprint 10 blocker was scheduled Live Stack run `26702182436`, which reached the app flow but failed because the acceptance marker contract required offline/spec markers from live runtime logs. Sprint 10 fixes the mapping contract by marking those scenarios as offline/spec executable evidence rather than live runtime markers.

A fresh scheduled Live Stack E2E run is still required after merge for final release readiness. If the live stack is unavailable, closure requires an explicit accepted waiver/blocker reference.

## PR and closure state

- Integrated PR: #478 (`chore: close Sprint 10 readiness debt`).
- Milestone 10: keep open until #478 CI is green, #478 is merged, all issues are closed by the merge, final `main` CI is green, and the Live Stack E2E result or accepted waiver/blocker is recorded.
