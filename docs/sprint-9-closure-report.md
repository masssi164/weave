# Sprint 9 closure report: Product Readiness Waterfall

Status: closure-ready audit, 2026-05-31.

## Closure verdict

Sprint 9 is functionally closed: the GitHub milestone has zero open issues at the time of this report update. The latest implementation `main` CI for head `68e8d65d5cf2a3a648e9aa9963a3be05c3b86dcc` is green; final closure now depends on this closure-report PR and milestone-close verification.

## GitHub truth

- Milestone: `Sprint 9 — Product Readiness Waterfall`.
- Open issues at update time: 0.
- Final implementation head before this report: `68e8d65` (`docs(marketing): ship README v2 claim matrix (#458)`).
- Latest implementation main CI runs: #464 run `26697124472` success, #465 run `26697126259` success, #458 run `26697128118` success.

## Issue DAG and final state

| Issue | Phase | Closing evidence | Final state |
| --- | --- | --- | --- |
| #436 | 0 — governance | PR #460 readiness evidence plus this closure report. | Closed. |
| #437 | 1 — docs foundation | PR #453 and PR #458. | Closed. |
| #438 | 1 — registry/portability primitives | PR #454, #457, #461, #464. | Closed. |
| #439 | 1 — core domains | PR #455, #456, #465. | Closed. |
| #440 | 2 — identity | PR #462 plus #464 identity/audit apply-gate blockers. | Closed. |
| #441 | 2 — admin console | PR #456 plus #460/#464 evidence. | Closed. |
| #442 | 2 — provider migration contracts | PR #461, #457, #464. | Closed. |
| #443 | 3 — work surfaces | PR #456, #460, #465. | Closed. |
| #444 | 3 — Boards | PR #465 plus #456/#464 gate foundations. | Closed. |
| #445 | 3 — Calls | PR #465. | Closed. |
| #446 | 4 — Weaver baseline | PR #459. | Closed. |
| #447 | 4 — Weaver policy | PR #459. | Closed. |
| #448 | 4 — Weaver tools | PR #459. | Closed. |
| #449 | 5 — quality hardening | PR #459 and PR #460. | Closed. |
| #450 | 5 — full product-readiness vertical | PR #460. | Closed. |
| #451 | 5 — README v2/claim matrix | PR #458. | Closed. |

## Merged PR order and evidence

| Order | PR | Main commit | Label | Evidence |
| --- | --- | --- | --- | --- |
| 1 | #453 canonical domain foundation docs | `bcec006` | `release-notes-feature` | PR checks green. |
| 2 | #454 canonical domain registry v1 | `5590098` | `release-notes-feature` | PR checks green; main CI green. |
| 3 | #455 Space anchor | `06bfb0d` | `release-notes-feature` | PR checks green; main CI green. |
| 4 | #461 no-unaccounted-data-loss contract | `7f0b467` | `release-notes-feature` | PR checks green; main CI green. |
| 5 | #457 server portability contracts | `7e1adb5` | `release-notes-feature` | PR checks green; main CI green. |
| 6 | #456 admin control-plane apply gates | `943c802` | `release-notes-feature` | PR checks green; main CI green. |
| 7 | #459 governed Weaver runtime/tool contract | `c832d05` | `release-notes-feature` | PR checks green; main CI green. |
| 8 | #460 product-readiness/e2e evidence | `b7e4431` | `release-notes-feature` | PR checks green; main CI `26696488056` green. |
| 9 | #462 Keycloak dry-run hardening | `72e97ac` | `release-notes-feature` | PR checks green; main CI `26696489550` green. |
| 10 | #464 migration apply gates | `08d62b1` | `release-notes-feature` | PR checks green; main CI `26697124472` success. |
| 11 | #465 Boards/Calls/domain readiness contracts | `19d0cc1` | `release-notes-feature` | PR checks green; main CI `26697126259` success. |
| 12 | #458 README v2 claim matrix | `68e8d65` | `release-notes-feature` | PR checks green; main CI `26697128118` success. |

## Gates and evidence

Local gates run across the implementation PRs included server, acceptance, spec, docs, release evidence, targeted Flutter release-spine tests, and PR-level GitHub Gradle CI/Release Notes Label Check. This closure report must additionally pass:

- `WEAVE_DOCS_VENV=build/docs-venv ./gradlew docsCheck releaseEvidenceCheck --console=plain`

## Release and RC impact

Sprint 9 establishes the product-readiness waterfall evidence for v0.1 dogfood-production posture: provider-neutral member surfaces, admin/operator control plane, identity and portability gates, Boards/Calls readiness contracts, governed Weaver policy boundaries, quality evidence, and README claim matrix. No production release or RC tag is published by this report.

## Remaining gate

- Wait for the latest post-merge `main` CI for #464/#465/#458 to complete green.
- Merge this closure report after docs/release checks pass.
- Verify the milestone remains at zero open issues, then close the milestone.
