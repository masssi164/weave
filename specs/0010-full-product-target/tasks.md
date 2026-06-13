# Tasks: Full Weave target product contract

**Spec corpus commit/ID**: `ebd342904b4fce4a71efdf1edd3be2635e5ede7c / WEAVE target product steering + domains`
**Repo conformance spec**: `specs/0010-full-product-target/spec.md`
**Plan**: `specs/0010-full-product-target/plan.md`

Task format: `- [ ] T001 [P?] [US?/Area] Description with exact path and gate`

## Phase 0: Truth recovery

- [x] T001 [Spec] Re-read `specs/weave-specs.lock.json`, relevant corpus files, `AGENTS.md`, `.specify/memory/constitution.md`, SDD docs, and handoff.
- [x] T002 [Spec] Verify branch/status and quarantine unrelated `tmp/` from commits.
- [x] T003 [Spec] Verify current GitHub PR/release-blocker context from Stage-0 and repo truth.

## Phase 1: Acceptance and contracts first

- [x] T010 [Spec] Create target-product Spec Kit artifacts under `specs/0010-full-product-target/`.
- [x] T011 [Acceptance] Create `e2e/features/weave_spec_0010_acceptance.feature` for setup/governance, space work, provider change, evidence/audit.
- [x] T012 [Acceptance] Map target-product scenarios in `e2e/scenario_mappings.json`.
- [x] T013 [Contracts] Add/repair provider-change and Decisions/Evidence contract fixtures where gaps remain.
- [x] T014 [Evidence] Run `./gradlew specCorpusConformance`, `./gradlew specContract`, and `./gradlew acceptanceContract`; expected red/green state documented.

## Phase 2: Implementation slices

- [ ] T020 [P] [US1/Admin] Implement or repair Control organization setup/provider mapping evidence in `admin-console/`, `server/`, and `e2e/`; gate `./gradlew adminCi serverCi acceptanceContract`. (GitHub issue #731)
- [ ] T021 [P] [US2/Member] Implement or repair Space-work cross-domain context/evidence in `client/`, `server/`, and `e2e/`; gate `./gradlew clientCi serverCi acceptanceContract`. (GitHub issue #732)
- [ ] T022 [US1/Provider] Implement provider-change dry-run/cutover/rollback contract gaps in `server/`, `infra/`, and `e2e/`; gate `./gradlew serverCi infraStatic acceptanceContract`. (GitHub issue #733)
- [ ] T023 [P] [US2/Evidence] Implement Decisions/Evidence domain evidence/export gaps in `server/`, `client/`, and `e2e/`; gate `./gradlew serverCi clientCi acceptanceContract`. (GitHub issue #734)

## Phase 3: Cross-cutting quality

- [ ] T030 [A11y] Verify screen-reader/non-color-only behavior for member/admin target flows.
- [ ] T031 [Support] Verify support-safe diagnostics and no raw provider/secret leakage.
- [ ] T032 [Audit] Verify audit/policy states for provider changes, decisions/evidence, and Weaver actions.
- [ ] T033 [Docs] Update product/developer/admin docs without release/v0.1 claims.

## Phase 4: Integration and handoff

- [ ] T040 Run required area gates.
- [ ] T041 Run `./gradlew ci` for cross-stack/release-relevant changes.
- [ ] T042 Run Integration-Gate or Optimization-Review; loop on material findings.
- [x] T043 Open/update GitHub issues/PRs with this Spec Kit chain and exactly one release-notes label.
- [ ] T044 Keep spec status accepted until implementation tasks are complete; do not claim implemented from spec-only work.
