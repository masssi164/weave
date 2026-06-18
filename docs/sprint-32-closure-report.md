# Sprint 32 closure report — Beta readiness candidate

Status: **pre-closure / blocked by #835**. This report prepares the Sprint 32 release/demo/evidence closure for issue #836 after #830-#834 landed on `dev`. It must not be used as final sprint closure until #835 is merged, linked, and the closure gate is refreshed from GitHub.

## Governing scope

- Pinned spec corpus: `specs/weave-specs.lock.json` at `6a6550dfef13f5582d1d3f1cdd5443564e246c1c`.
- Product direction: `docs/product-line-and-weaver-plan.md` keeps Weave as provider-neutral organization suite first, Admin/IDM/RBAC/readiness second, governed per-user Weaver later.
- Sprint claim gate: `docs/beta-readiness-claim-gates.md`.
- Release evidence bundle: `docs/evidence/sprint-32-beta-readiness-release-evidence.md`.
- Stage-0 snapshot: `release/sprint-32-beta-readiness-stage-0.json`.

## Issue DAG state

| Issue | Role | Evidence / PR | Current closure state |
| --- | --- | --- | --- |
| #830 | Define Beta readiness slice and claim gates. | PR #837; `docs/beta-readiness-claim-gates.md`. | Closed after merge to `dev`. |
| #831 | Adapter continuity dry-run without unaccounted data loss. | PR #838; `docs/evidence/sprint-32-adapter-continuity-dry-run.md`. | Closed after merge to `dev`; dry-run evidence only. |
| #832 | Admin Beta setup/control readiness preview. | PR #839. | Closed after merge to `dev`; release/demo wording remains bounded by this report. |
| #833 | Approval-required Weaver actions through user runtime. | PR #840; `docs/evidence/weaver-approval-runtime-boundary-issue-833.md`. | Closed after merge to `dev`; not a server approval oracle. |
| #834 | Member Client + Weaver Beta flow. | PR #841. | Closed after merge to `dev`; final a11y/E2E proof still belongs to #835. |
| #835 | Admin + User + Weaver E2E proof. | PR #842 is open/in flight; issue remains open until that PR merges and the evidence is refreshed. | **Blocking final Sprint 32/Beta closure.** |
| #836 | Release evidence and demo alignment. | This report and evidence bundle. | Prepared by this PR; final close only after #835. |

## Merged PR evidence

| PR | Title | Merge evidence | CI/release-label evidence |
| --- | --- | --- | --- |
| #837 | docs: define Beta readiness claim gates | Merged to `dev` at 2026-06-18T04:30:45Z. | GitHub Gradle CI success; `release-notes-feature`. |
| #838 | feat(server): add adapter continuity dry-run report | Merged to `dev` at 2026-06-18T04:39:54Z. | GitHub Gradle CI success; `release-notes-feature`. |
| #839 | feat(admin): add Beta readiness control preview | Merged to `dev` at 2026-06-18T04:30:50Z. | GitHub Gradle CI success; `release-notes-feature`. |
| #840 | feat(weaver): route approvals through runtime | Merged to `dev` at 2026-06-18T04:39:58Z. | GitHub Gradle CI success; `release-notes-feature`. |
| #841 | feat(client): add bounded Weaver beta helper flow | Merged to `dev` at 2026-06-18T04:40:01Z. | GitHub Gradle CI success; `release-notes-feature`. |

## Release/demo claim posture

Allowed demo wording: Sprint 32 is a guarded Beta candidate with merged claim gates, adapter-continuity dry-run, Admin readiness preview, governed Weaver approval boundary, and member Client + Weaver flow on `dev`.

Forbidden until #835 and final closure:

- Beta complete / GA / public launch / production SLA.
- MCP as the member-facing product surface.
- Lossless or production-applied provider switching.
- Unrestricted autonomous Weaver, default PA availability, or raw provider access.

## Gates and evidence

Required for this #836 preparation PR:

- `./gradlew docsCheck`
- `./gradlew releaseEvidenceCheck`

Final Sprint 32 closure still requires a refreshed GitHub closure gate after #835 merges: zero open Sprint 32 Beta issues, closure report on the target release truth branch, green CI for the final integration head, and evidence links for E2E/acceptance, adapter dry-run, runtime approvals, accessibility smoke, and Stage-0/final snapshots.

## Known limitations / blockers

- #835 remains the only open issue in the canonical #830-#836 Beta closure set besides this #836 preparation issue; PR #842 is open/in flight and blocks final Admin + User + Weaver E2E proof and critical a11y smoke until merged.
- Local dirty worktrees are not release truth. Use GitHub PRs/checks, protected branches, and checked-in evidence artifacts.
- Sprint 32 currently targets `dev`; promotion to `main`, RC tagging, or production publication is outside this #836 preparation PR.

## Next safe action

Merge PR #842 for #835 E2E evidence first. Then refresh this report and `release/sprint-32-beta-readiness-stage-0.json` from GitHub, rerun `docsCheck` and `releaseEvidenceCheck`, and only then close #836 / the Sprint 32 milestone if the GitHub closure gate is green.
