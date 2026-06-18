# Sprint 32 Beta readiness release evidence

Issue: #836
Status: pre-closure evidence bundle; final Beta closure remains blocked until #835 / PR #842 is merged and linked.

## Canonical source boundary

This evidence bundle is derived from remote GitHub and checked-in artifacts on `origin/dev`, not from a dirty local worktree. The governing Beta claim boundary is `docs/beta-readiness-claim-gates.md`; this page only assembles release/demo evidence for review.

## Evidence map

| Gate | Evidence | Current status |
| --- | --- | --- |
| CI for merged Sprint 32 slices | PRs #837, #838, #839, #840, and #841 are merged to `dev`; each had successful GitHub Gradle CI and exactly one release-notes label. | Present for component slices. |
| E2E / acceptance evidence | Issue #835 is the owning Admin + User + Weaver E2E proof; PR #842 is open/in flight. | Blocked until PR #842 merges. Do not claim final Beta readiness before that merge. |
| Adapter-continuity dry-run | `docs/evidence/sprint-32-adapter-continuity-dry-run.md`; PR #838. | Present as dry-run/report evidence only; no production apply or lossless migration claim. |
| Runtime approval evidence | `docs/evidence/weaver-approval-runtime-boundary-issue-833.md`; PR #840. | Present for approval-required shared-state action routing through the user runtime. |
| A11y smoke | #835 / PR #842 must publish or link the critical Admin/User/Weaver accessibility smoke; #834 component flow is present on `dev`. | Pending #842 merge. |
| Stage-0 snapshot | `release/sprint-32-beta-readiness-stage-0.json` records the pre-closure issue/PR/evidence state. | Present in this PR; final closure must refresh after #835. |
| Release notes / claim matrix | `docs/release-notes/unreleased.md`, `docs/product-trust-provider-choice-claim-matrix.md`, and `docs/beta-readiness-claim-gates.md`. | Updated/aligned as guarded Beta evidence; final closure remains blocked by #835. |

## Demo wording allowed now

Allowed for internal demo/review:

> Sprint 32 has merged the Beta claim gates, adapter-continuity dry-run, Admin readiness preview, governed Weaver approval boundary, and member Client + Weaver flow. The final end-to-end Beta proof is still in flight under #835, so this is a guarded Beta candidate, not a closed Beta readiness claim.

Do not say:

- Beta complete, GA, public launch, customer-ready, production SLA, or legally compliant by default.
- MCP is the user-facing product surface.
- Provider switching is lossless or production-applied.
- Weaver is unrestricted, default-enabled for all users, or allowed raw provider access.

## Known limitations and blockers

- #835 remains the explicit open P0 Beta blocker for final end-to-end Admin + User + Weaver proof and accessibility smoke; PR #842 is open/in flight and not final until merged.
- Sprint 32 evidence is on `dev`; final release truth must be promoted through the normal lane/RC flow before any stable `main` release claim.
- Adapter continuity is a dry-run/no-unaccounted-data-loss report gate, not a production migration apply gate.
- Weaver shared-state actions require runtime approval receipts; persistent approval and broader tool coverage remain future scoped work.
