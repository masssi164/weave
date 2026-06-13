# Tasks: Weaver governed per-user personal assistant target

**Spec corpus commit/ID**: `ebd342904b4fce4a71efdf1edd3be2635e5ede7c / WEAVE-DOMAIN-WEAVER-GOVERNED-PA`
**Repo conformance spec**: `specs/0011-weaver-governed-pa-target/spec.md`
**Plan**: `specs/0011-weaver-governed-pa-target/plan.md`

Task format: `- [ ] T001 [P?] [US?/Area] Description with exact path and gate`

## Phase 0: Truth recovery

- [x] T001 [Spec] Re-read corpus Weaver domain, repo Weaver specs, handoff, Massimo answers, and SDD docs.
- [x] T002 [Spec] Verify `weave-co-leader` remains implementation staff only and not product vocabulary.
- [x] T003 [Spec] Verify issue #719 remains the live evidence blocker for isolated Keycloak/MCP proof.

## Phase 1: Acceptance and contracts first

- [x] T010 [Spec] Create Weaver target Spec Kit artifacts under `specs/0011-weaver-governed-pa-target/`.
- [x] T011 [Acceptance] Create `e2e/features/weave_spec_0011_acceptance.feature` for eligibility, policy preview, memory isolation, approvals, audit, heartbeat/fallback.
- [x] T012 [Acceptance] Map Weaver target scenarios in `e2e/scenario_mappings.json`.
- [ ] T013 [Contracts] Add/repair runtime profile, approval receipt, memory scope, heartbeat, and audit fixtures.
- [x] T014 [Evidence] Run `./gradlew specCorpusConformance`, `./gradlew specContract`, and `./gradlew acceptanceContract`; expected red/green state documented.

## Phase 2: Implementation slices

- [ ] T020 [US1/Admin] Add/repair Admin Console Weaver policy + `weaver-group` eligibility preview; paths `admin-console/`, `server/`, `e2e/`; gate `./gradlew adminCi serverCi acceptanceContract`. (GitHub issue #735)
- [ ] T021 [US2/Runtime] Add/repair signed per-user `WeaverRuntimeProfile` generation and revocation; paths `server/`; gate `./gradlew serverCi`. (GitHub issue #736)
- [ ] T022 [US2/Memory] Add/repair per-user memory scope/export/delete/retention contract; paths `server/`, `e2e/`; gate `./gradlew serverCi acceptanceContract`. (GitHub issue #737)
- [ ] T023 [US2/Tools] Add/repair domain-first MCP tool discovery/execution with scopes and provider facade calls; paths `server/`, `e2e/`; gate `./gradlew serverCi acceptanceContract`. (GitHub issue #738)
- [ ] T024 [US2/Approvals] Add/repair approval receipt validation, stale-version fail-closed behavior, and accessible UX hooks; paths `server/`, `client/`, `e2e/`; gate `./gradlew serverCi clientCi acceptanceContract`. (GitHub issue #738)
- [ ] T025 [US3/Audit] Add/repair support-safe audit for model/channel/memory/tool/approval/denial/heartbeat events; paths `server/`, `admin-console/`, `e2e/`; gate `./gradlew serverCi adminCi acceptanceContract`. (GitHub issue #738)
- [ ] T026 [US2/Automation] Add/repair heartbeat/automation schedule/scope/rate-limit policy and denial evidence; paths `server/`, `admin-console/`; gate `./gradlew serverCi adminCi`. (GitHub issue #738)

## Phase 3: Cross-cutting quality

- [ ] T030 [A11y] Verify approval/fallback and admin policy previews are screen-reader accessible and not color-only.
- [ ] T031 [Support] Verify no raw provider secrets, personal OpenClaw paths, or private runtime config in profiles/logs/docs/support bundles.
- [ ] T032 [Audit] Verify policy decisions, receipt ids, profile hashes, and denial reasons are emitted support-safely.
- [ ] T033 [Docs] Update product/developer/admin docs with governed Weaver target and no release/v0.1 claims.

## Phase 4: Integration and handoff

- [ ] T040 Run required area gates.
- [ ] T041 Run isolated live evidence only when non-destructive and explicitly safe; otherwise keep issue #719 open.
- [ ] T042 Run Integration-Gate or Optimization-Review; loop on material findings.
- [x] T043 Open/update GitHub issues/PRs with this Spec Kit chain and exactly one release-notes label.
- [ ] T044 Keep spec status accepted until implementation tasks are complete; do not claim implemented from spec-only work.
