# Sprint 8 closure report: Canonical Domains & Portable Provider Contracts

Status: closure-ready audit, 2026-05-31.

## Closure verdict

Sprint 8 is functionally closed: the GitHub milestone has zero open issues at the time of this report update. The latest implementation `main` CI for head `68e8d65d5cf2a3a648e9aa9963a3be05c3b86dcc` is green; final closure now depends on this closure-report PR and milestone-close verification.

## GitHub truth

- Milestone: `Sprint 8 — Canonical Domains & Portable Provider Contracts`.
- Open issues at update time: 0.
- Final implementation head before this report: `68e8d65` (`docs(marketing): ship README v2 claim matrix (#458)`).
- Latest implementation main CI runs: #464 run `26697124472` success, #465 run `26697126259` success, #458 run `26697128118` success.

## Issue DAG and final state

| Issue | Dependency role | Closing evidence | Final state |
| --- | --- | --- | --- |
| #425 | Sprint governance root. | PR #452. | Closed. |
| #426 | Foundation docs. | PR #453. | Closed. |
| #427 | Canonical domain registry v1. | PR #454. | Closed. |
| #428 | Space anchor. | PR #455. | Closed. |
| #429 | No-unaccounted-data-loss contract. | PR #461, reinforced by #457/#464. | Closed. |
| #430 | Keycloak desired realm dry-run. | PR #462. | Closed. |
| #431 | Admin Console domain-first setup/provider switching gates. | PR #456 plus #464 apply-gate evidence. | Closed. |
| #432 | Boards portability parity. | PR #465. | Closed. |
| #433 | Governed OpenClaw-derived runtime profile. | PR #459. | Closed. |
| #434 | Sprint 8 domain-control-plane acceptance evidence. | PR #460. | Closed. |
| #435 | README sovereign collaboration rewrite. | PR #458. | Closed. |
| #283 | Legacy identity/Boards vertical mapping prototype. | Superseded and closed by #462, #465, #456, #464 evidence. | Closed. |

## Merged PR order and evidence

| Order | PR | Main commit | Label | Evidence |
| --- | --- | --- | --- | --- |
| 1 | #452 Sprint 8 delivery board policy | `cdc2e05` | `release-notes-skip` | PR checks green. |
| 2 | #453 canonical domain foundation docs | `bcec006` | `release-notes-feature` | PR checks green. |
| 3 | #454 canonical domain registry v1 | `5590098` | `release-notes-feature` | PR checks green; main CI `26695569929` green. |
| 4 | #455 Space anchor | `06bfb0d` | `release-notes-feature` | PR checks green; main CI `26695769160` green. |
| 5 | #461 no-unaccounted-data-loss contract | `7f0b467` | `release-notes-feature` | PR checks green; main CI `26696213184` green. |
| 6 | #457 server domain portability contracts | `7e1adb5` | `release-notes-feature` | PR checks green; main CI `26696214854` green. |
| 7 | #456 Admin Console apply gates | `943c802` | `release-notes-feature` | PR checks green; main CI `26696216153` green. |
| 8 | #459 governed Weaver runtime profile | `c832d05` | `release-notes-feature` | PR checks green; main CI `26696217413` green. |
| 9 | #460 Sprint 8/9 acceptance evidence | `b7e4431` | `release-notes-feature` | PR checks green; main CI `26696488056` green. |
| 10 | #462 Keycloak dry-run hardening | `72e97ac` | `release-notes-feature` | PR checks green; main CI `26696489550` green. |
| 11 | #464 migration apply gates | `08d62b1` | `release-notes-feature` | PR checks green; main CI `26697124472` success. |
| 12 | #465 Boards/Calls/domain readiness contracts | `19d0cc1` | `release-notes-feature` | PR checks green; main CI `26697126259` success. |
| 13 | #458 README v2 claim matrix | `68e8d65` | `release-notes-feature` | PR checks green; review threads resolved/outdated; main CI `26697128118` success. |

## Local gates for this report

Run before merging this closure report:

- `WEAVE_DOCS_VENV=build/docs-venv ./gradlew docsCheck releaseEvidenceCheck --console=plain`

## Release and RC impact

Sprint 8 locks the canonical domain, Spaces, portability, Admin Console gate, identity dry-run, Boards parity, acceptance evidence, and README foundation needed for dogfood-production readiness. It does not claim a production release tag; release promotion remains governed by release evidence and final `main` CI.

## Remaining gate

- Merge this closure report after its docs/release checks pass.
- Verify both milestones remain at zero open issues, then close the milestones.
