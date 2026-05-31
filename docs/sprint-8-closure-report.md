# Sprint 8 closure report: Canonical Domains & Portable Provider Contracts

Status: audited with Sprint 10 follow-up debt, 2026-05-31.

## Closure verdict

Sprint 8 implementation issues are closed, and the closure-audit report landed on `main` at `9f458f409ed58798e36812b085839d18125bb8fc` (#463). Repository readiness is not product-ready yet: Sprint 10 carries release-blocking follow-up debt, and Live Stack E2E for the closure-audit head failed. Treat this report as historical closure plus current blockers, not an RC or production-release claim.

## GitHub truth

- Milestone: `Sprint 8 — Canonical Domains & Portable Provider Contracts`.
- Open Sprint 8 issues at update time: 0.
- Closure-audit head: `9f458f4` (`docs: add Sprint 8 and 9 closure audits (#463)`).
- Latest verified CI for the closure-audit head: CI run `26697473651` success; Live Stack E2E run `26702182436` failure.
- Final implementation main CI runs before the closure audit: #464 run `26697124472` success, #465 run `26697126259` success, #458 run `26697128118` success.

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

The closure-audit PR (#463) recorded docs/release evidence before merge. Current Sprint 10 maintenance for this report should rerun the smallest relevant docs/spec gates.

## Release and RC impact

Sprint 8 locks the canonical domain, Spaces, portability, Admin Console gate, identity dry-run, Boards parity, acceptance evidence, and README foundation needed for dogfood-production readiness. It does not claim a production release tag or RC readiness; release promotion remains blocked until Sprint 10 release blockers and Live Stack E2E evidence are resolved.

## Sprint 10 follow-up debt

- #467 restore canonical domain registry drift gate.
- #471 choose one canonical portability schema family.
- #475 fix duplicate `WEAVE-SPEC-0004` traceability.
- #476 keep Sprint 8/9 closure wording accurate.
- #477 deepen registry, Calls, waiver, and provider-vocabulary contracts.
