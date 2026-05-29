# Tasks: Context-driven workflow primitives

**Spec**: `specs/0002-context-driven-workflows/spec.md`  
**Plan**: `specs/0002-context-driven-workflows/plan.md`

## Phase 0: Truth recovery

- [x] T001 [Spec] Re-read `AGENTS.md`, `.specify/memory/constitution.md`, product-line direction, and issue #218.
- [x] T002 [Spec] Verify branch and local diff state for `issue-218-workflow-contract`.
- [x] T003 [Spec] Verify issue #218 acceptance asks for templates, Context Graph references, agent rules, sample workflows, and MVP slice.

## Phase 1: Acceptance and contracts first

- [x] T010 [Spec] Add repo-local proposed workflow contract in `specs/0002-context-driven-workflows/spec.md`.
- [x] T011 [Acceptance] Record that Gherkin/mapping is deferred until persisted backend workflow/user journey exists.
- [x] T012 [Contracts] Keep Context Graph/server ownership as a required future contract before execution.
- [x] T013 [Evidence] Run `./gradlew specContract`.

## Phase 2: Implementation slices

- [x] T020 [US1] Extend preview primitives in `client/lib/features/workflows/domain/entities/workflow_preview.dart`.
- [x] T021 [US1] Add sample workflow previews in `client/lib/features/workflows/presentation/providers/workflow_preview_provider.dart`.
- [x] T022 [US1] Update workflow preview provider/widget tests.
- [x] T023 [US1] Run targeted Flutter workflow tests.

## Phase 3: Cross-cutting quality

- [x] T030 [A11y] Keep workflow preview linear, non-drag, text-first, and semantics-covered.
- [x] T031 [Support] Keep context references support-safe and provider-neutral.
- [x] T032 [Audit] Model approval, dry-run, and audit-required flags.
- [x] T033 [Docs] Document release-notes expectation in the spec.

## Phase 4: Integration and handoff

- [x] T040 Run required area gates.
- [ ] T041 Run `./gradlew clientCi` before merge after branch changes are committed; local uncommitted-diff check fails while this patch is still unstaged.
- [x] T042 Run architecture-contract review and resolve API/domain drift.
- [ ] T043 Fill PR body with spec ID, acceptance/evidence, risks, and fallback review evidence.
- [x] T044 Keep spec status `proposed` until backend execution and policy names are accepted.
- [x] T045 Agent handoff: preserve durable decisions, evidence, blockers, and next safe action only.
