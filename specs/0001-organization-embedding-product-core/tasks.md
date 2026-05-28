# Tasks: Organization embedding and provider-neutral product core

**Spec**: `specs/0001-organization-embedding-product-core/spec.md`  
**Plan**: `specs/0001-organization-embedding-product-core/plan.md`

Task format: `- [ ] T001 [P?] [US?/Area] Description with exact path and gate`

## Phase 0: Truth recovery

- [x] T001 [Spec] Re-read `AGENTS.md`, `.specify/memory/constitution.md`, `docs/spec-driven-development.md`, and `docs/product-line-and-weaver-plan.md` orientation.
- [x] T002 [Spec] Verify branch starts from current `origin/main` and no unrelated local changes are present.
- [x] T003 [Spec] Create/verify linked GitHub issue #381 and release-notes label expectation.

## Phase 1: Product-core clarification

- [ ] T010 [Decision] Confirm whether `WEAVE-SPEC-0001` starts with Organization embedding / IDM / Policy / Readiness or another first slice.
- [ ] T011 [Decision] Confirm first-spec product-core surface: Member Client, Admin/Provider Control Plane, or provider-neutral suite contract across Member + Admin + Operator.
- [ ] T012 [Decision] Confirm Weaver scope: placeholder category vs concrete runtime profile generation acceptance.
- [ ] T013 [Decision] Confirm mandatory vs optional provider categories for v0.1/v0.2.
- [ ] T014 [Decision] Confirm minimal stable capability vocabulary for first user-visible release.

## Phase 2: Acceptance and contracts first

- [ ] T020 [Acceptance] Add or update product-language Gherkin after T010-T014.
- [ ] T021 [Acceptance] Update `e2e/scenario_mappings.json` with stable markers after story split.
- [ ] T022 [Contracts] Add API/event/provider contract files for the selected first implementation surface.
- [ ] T023 [Evidence] Run `./gradlew specContract` and `./gradlew acceptanceContract`; document expected draft/proposed state.

## Phase 3: Implementation slices

Implementation tasks are intentionally blocked until the spec moves from draft to proposed/accepted with product-core decisions resolved.

## Phase 4: Integration and handoff

- [ ] T040 Run required area gates.
- [ ] T041 Run Integration-Gate or Optimization-Review; loop on material findings.
- [ ] T042 Fill PR body with spec ID, acceptance/evidence, risks, and fallback review evidence.
- [ ] T043 Update spec status/version or document why it remains draft/proposed.
- [ ] T044 Agent handoff: preserve durable decisions, evidence, blockers, and next safe action only.
