# Sprint 32 Beta readiness release evidence

Issue: #836
Status: Beta readiness slice evidence bundle refreshed after #835 / PR #842 merged; #830-#836 closure depends on #836 merge and green CI. This does not claim overall Sprint 32 milestone completion.

## Canonical source boundary

This evidence bundle is derived from remote GitHub and checked-in artifacts on `origin/dev`, not from a dirty local worktree. The governing Beta claim boundary is `docs/beta-readiness-claim-gates.md`; this page only assembles release/demo evidence for review.

## Evidence map

| Gate | Evidence | Current status |
| --- | --- | --- |
| CI for merged Sprint 32 slices | PRs #837, #838, #839, #840, #841, and #842 are merged to `dev`; each had successful GitHub Gradle CI and exactly one release-notes label. | Present for component and E2E slices. |
| E2E / acceptance evidence | Issue #835 and PR #842 are historical. Their Beta-path fixture is retired by `docs/evidence/sprint-32-beta-path-evidence.md`; current ARC/MCP evidence is tracked separately. | Historical only; it cannot promote the current architecture. |
| Adapter-continuity dry-run | `docs/evidence/sprint-32-adapter-continuity-dry-run.md`; PR #838. | Present as dry-run/report evidence only; no production apply or lossless migration claim. |
| Runtime approval evidence | `docs/evidence/weaver-approval-runtime-boundary-issue-833.md`; PR #840. | Present for approval-required shared-state action routing through the user runtime. |
| A11y smoke | #835 / PR #842 recorded a historical component smoke; the obsolete runtime evidence is retired by `docs/evidence/sprint-32-beta-path-evidence.md`. | Historical only; current ARC/admin/member accessibility needs current evidence. |
| Stage-0 snapshot | `release/sprint-32-beta-readiness-stage-0.json` records the refreshed issue/PR/evidence state after #842 merged. | Present in this PR; #836 closure must re-check CI at merge time. |
| Release notes / claim matrix | `docs/release-notes/unreleased.md`, `docs/product-trust-provider-choice-claim-matrix.md`, and `docs/beta-readiness-claim-gates.md`. | Updated/aligned as guarded Beta evidence; #830-#836 slice closure remains blocked by #836 review/merge and CI verification. |

## Demo wording allowed now

Allowed for internal demo/review:

> Sprint 32 has merged the #830-#835 Beta readiness slice: claim gates, adapter-continuity dry-run, Admin readiness preview, governed Weaver approval boundary, member Client + Weaver flow, and Admin + User + Weaver E2E/accessibility smoke evidence. This is still a guarded Beta candidate until #836 release evidence merges and CI remains green; the overall Sprint 32 milestone remains open for other P0 lanes.

Do not say:

- Beta complete, GA, public launch, customer-ready, production SLA, or legally compliant by default.
- MCP is the user-facing product surface.
- Provider switching is lossless or production-applied.
- Weaver is unrestricted, default-enabled for all users, or allowed raw provider access.

## Known limitations and blockers

- #835 is closed and PR #842 is merged; #836 covers release/demo alignment for the #830-#836 Beta readiness slice only. The overall Sprint 32 milestone remains open pending remaining P0 lanes outside #830-#836, including #771, #787, #792, #794, and #795.
- Sprint 32 evidence is on `dev`; final release truth must be promoted through the normal lane/RC flow before any stable `main` release claim.
- Adapter continuity is a dry-run/no-unaccounted-data-loss report gate, not a production migration apply gate.
- Weaver shared-state actions require runtime approval receipts; persistent approval and broader tool coverage remain future scoped work.
