# Tasks: Admin-Suite and provider-neutral product core

**Spec**: `specs/0001-organization-embedding-product-core/spec.md`  
**Plan**: `specs/0001-organization-embedding-product-core/plan.md`

Task format: `- [ ] T001 [P?] [US?/Area] Description with exact path and gate`

## Phase 0: Truth recovery

- [x] T001 [Spec] Re-read repo specs/docs and recover current GitHub issue/PR state.
- [x] T002 [Spec] Rebase `spec/weave-0001-org-embedding` onto current `origin/main`.
- [x] T003 [Spec] Verify release-notes label expectation: `release-notes-skip` for this spec-only PR.

## Phase 1: Product-core decisions

- [x] T010 [Decision] Resolve first product-core slice from Massimo's Forms response: Admin-Suite + provider neutrality.
- [x] T011 [Decision] Resolve first-spec product surface: provider-neutral suite contract across Member + Admin + Operator, with Admin-Suite first-class.
- [x] T012 [Decision] Resolve Weaver scope: Weaver/AI runtime is out of scope for WEAVE-SPEC-0001.
- [x] T013 [Decision] Resolve mandatory v0.1 domains: IDM/RBAC, Chat/Channels, Files/Docs, Boards/Tasks, Calendar/Events, Meetings, Forms/Contacts; Health/Readiness cross-cutting.
- [x] T014 [Decision] Resolve capability vocabulary: `available`, `disabled_by_policy`, `not_configured`, `degraded`, `unavailable`, `coming_later`.

## Phase 2: Issue DAG and contracts first

- [x] T020 [DAG] Create #386 for provider-neutral domain and capability model.
- [x] T021 [DAG] Create #387 for Admin-Suite readiness/setup UX contract.
- [x] T022 [DAG] Create #388 for provider switch and portable export/import contract.
- [x] T023 [DAG] Create #389 for acceptance and evidence mapping.
- [x] T024 [Spec] Update `spec.md`, `plan.md`, `tasks.md`, and `traceability.yaml` with decisions and DAG.

## Phase 3: Implementation slices

Implementation tasks are intentionally split into follow-up PRs from the issue DAG:

- [x] T030 [#386] Add provider-neutral domain/capability contracts and smallest relevant server/client/admin gate.
- [x] T031 [#387] Add Admin-Suite readiness/setup UX contract and gate.
- [ ] T032 [#388] Add provider switch/export-import/cutover/rollback contract and gate.
- [ ] T033 [#389] Add product-language acceptance/evidence mapping and gate.
- [ ] T034 [#710/#591] Reflect public/customer-ready claim gates in GitHub issue subtasks: complete automated evidence, manual AT signoff, zero open release blockers, target-branch verification, and support-safe release evidence.
- [ ] T035 [#719] Keep Weaver visible only as opt-in governed beta/v1 work until isolated Keycloak/MCP live-runtime evidence exists.


## Phase 4: Integration and handoff

- [x] T040 Run `./gradlew specContract specContractTest acceptanceContract --console=plain`.
- [x] T041 Push branch and update PR #382 to non-draft only if gates pass.
- [x] T042 Update #381 with decision record and link the integrated PR/DAG.
- [x] T043 Inspect GitHub CI for #382.
- [x] T044 Merge PR #382 when CI/labels/branch protection permit.
- [x] T045 Fast-forward `main`, close/confirm #381, and update sprint closure/release evidence.
