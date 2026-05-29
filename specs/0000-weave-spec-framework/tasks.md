# Tasks: Weave spec-driven development framework

**Spec**: `specs/0000-weave-spec-framework/spec.md`  
**Plan**: `specs/0000-weave-spec-framework/plan.md`

## Phase 0: Truth recovery

- [x] T001 [Spec] Re-read `AGENTS.md`, `docs/weave-operating-model.md`, `docs/acceptance-contracts.md`, and `docs/quality-and-evidence.md`.
- [x] T002 [Spec] Check repo branch/status and existing Gradle gates.
- [x] T003 [Research] Compare Spec Kit template concepts and agent-role templates; adapt, do not blindly import.

## Phase 1: Framework artifacts

- [x] T010 [Spec] Add `.specify/memory/constitution.md`.
- [x] T011 [Spec] Add Weave spec/plan/task templates.
- [x] T012 [Agents] Add Weave assistant briefing templates with repo-safe runtime-boundary rules.
- [x] T013 [Docs] Add `specs/README.md` and framework spec files.
- [x] T014 [Research] Run NotebookLM deep research without preselected sources for orchestrator/subagent/ACP team patterns.
- [x] T015 [Agents] Remove deployable-looking agent team config examples from repo-local templates.
- [x] T016 [Docs] Add `docs/agent-team-orchestration.md` with logical roles, loop, rubric, guardrails, and runtime-boundary language.

## Phase 2: Guard and wiring

- [x] T020 [Tools] Add `tools/spec_contract_check.py`.
- [x] T021 [Tools] Add `tools/spec_contract_check_test.py` fixture tests.
- [x] T022 [Build] Add `specContract` and `specContractTest` Gradle tasks plus CI evidence wiring.
- [x] T023 [Build] Add `make spec-contract` and `make spec-contract-test` aliases.
- [x] T024 [Review] Add PR template spec/evidence fields.

## Phase 3: Validation

- [x] T030 [Gate] Run `./gradlew specContract`.
- [x] T031 [Gate] Run `./gradlew specContractTest`.
- [x] T032 [Gate] Run `./gradlew docsStructureCheck`.
- [x] T033 [Gate] Run `./gradlew acceptanceContract`.
- [x] T034 [Review] Run Optimization-Review and address material findings until none remain.
- [x] T035 [Gate] Re-run spec/docs gates after optimization loop.
