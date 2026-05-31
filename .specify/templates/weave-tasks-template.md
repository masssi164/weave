# Tasks: Replace with title

**Spec corpus commit/ID**: `<commit> / <WEAVE-...>`
**Repo conformance spec**: `specs/0000-slug/spec.md` when applicable
**Plan**: `specs/0000-slug/plan.md` when applicable

Task format: `- [ ] T001 [P?] [US?/Area] Description with exact path and gate`

## Phase 0: Truth recovery

- [ ] T001 [Spec] Re-read `specs/weave-specs.lock.json`, the relevant spec corpus files, `AGENTS.md`, `.specify/memory/constitution.md`, and referenced docs.
- [ ] T002 [Spec] Verify branch starts from current `origin/main` and no unrelated local changes are present.
- [ ] T003 [Spec] Verify linked issue/PR status and release-notes label expectations.

## Phase 1: Acceptance and contracts first

- [ ] T010 [Acceptance] Update or create product-language Gherkin under `e2e/features/` when product behavior changes.
- [ ] T011 [Acceptance] Update `e2e/scenario_mappings.json` with stable marker and executable evidence.
- [ ] T012 [Contracts] Add or update API/event/provider contract files before implementation.
- [ ] T013 [Evidence] Run `./gradlew specCorpusConformance`, `./gradlew specContract`, and `./gradlew acceptanceContract`; expected red/green state documented.

## Phase 2: Implementation slices

Group implementation by independently testable user story. Mark `[P]` only when files do not conflict.

- [ ] T020 [US1] Add failing tests/evidence in exact path.
- [ ] T021 [US1] Implement smallest code/doc change in exact path.
- [ ] T022 [US1] Run smallest meaningful gate and record result.

## Phase 3: Cross-cutting quality

- [ ] T030 [A11y] Verify screen-reader/non-color-only behavior where UI changes.
- [ ] T031 [Support] Verify support-safe diagnostics and no raw provider/secret leakage.
- [ ] T032 [Audit] Verify audit/policy state where capability or admin actions change.
- [ ] T033 [Docs] Update product/developer/admin docs and release notes expectations.

## Phase 4: Integration and handoff

- [ ] T040 Run required area gates.
- [ ] T041 Run `./gradlew ci` for cross-stack/release-relevant changes.
- [ ] T042 Run Integration-Gate or Optimization-Review; loop on material findings.
- [ ] T043 Fill PR body with spec corpus commit/ID, acceptance/evidence, risks, and fallback review evidence.
- [ ] T044 Update spec status/version or document why it remains draft/proposed.
- [ ] T045 Assistant handoff: preserve durable decisions, evidence, blockers, and next safe action only.
