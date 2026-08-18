# Sprint 32 closure report — Beta readiness candidate

Status: **historical closure record, superseded by Agent Runtime Control v2**. This report records what the #830-#836 slice claimed in June 2026; its bespoke approval boundary, member opt-in/tool-grant flow, and associated fixtures were removed and must not be used as current release evidence. Current runtime truth is the pinned Agent Runtime Control spec, ADR 0012, and the current workload-identity/lifecycle evidence.

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
| #834 | Member Client + Weaver Beta flow. | PR #841. | Closed after merge to `dev`; complementary a11y/E2E proof is now covered by closed #835 / merged PR #842. |
| #835 | Admin + User + Weaver E2E proof. | PR #842; `docs/evidence/sprint-32-beta-path-evidence.md`. | Closed after merge to `dev`; GitHub shows PR #842 merged at 2026-06-18T08:21:23Z and issue #835 closed at 2026-06-18T08:21:26Z. |
| #836 | Release evidence and demo alignment. | This report and evidence bundle. | Prepared by this PR; final close depends on #836 review/merge and a still-green GitHub closure gate. |

## Merged PR evidence

| PR | Title | Merge evidence | CI/release-label evidence |
| --- | --- | --- | --- |
| #837 | docs: define Beta readiness claim gates | Merged to `dev` at 2026-06-18T04:30:45Z. | GitHub Gradle CI success; `release-notes-feature`. |
| #838 | feat(server): add adapter continuity dry-run report | Merged to `dev` at 2026-06-18T04:39:54Z. | GitHub Gradle CI success; `release-notes-feature`. |
| #839 | feat(admin): add Beta readiness control preview | Merged to `dev` at 2026-06-18T04:30:50Z. | GitHub Gradle CI success; `release-notes-feature`. |
| #840 | feat(weaver): route approvals through runtime | Merged to `dev` at 2026-06-18T04:39:58Z. | GitHub Gradle CI success; `release-notes-feature`. |
| #841 | feat(client): add bounded Weaver beta helper flow | Merged to `dev` at 2026-06-18T04:40:01Z. | GitHub Gradle CI success; `release-notes-feature`. |
| #842 | test: map Sprint 32 Beta path evidence | Merged to `dev` at 2026-06-18T08:21:23Z. | GitHub Gradle CI success; `release-notes-skip`. |

## Release/demo claim posture

Allowed demo wording: Sprint 32 is a guarded Beta candidate with merged claim gates, adapter-continuity dry-run, Admin readiness preview, governed Weaver approval boundary, member Client + Weaver flow, and Admin + User + Weaver E2E/accessibility evidence on `dev`.

Forbidden until final closure/promotion:

- Beta complete / GA / public launch / production SLA.
- MCP as the member-facing product surface.
- Lossless or production-applied provider switching.
- Unrestricted autonomous Weaver, default PA availability, or raw provider access.

## Gates and evidence

Required for this #836 preparation PR:

- `./gradlew docsCheck`
- `./gradlew releaseEvidenceCheck`

Final closure for the #830-#836 Beta readiness slice requires #836 review/merge, this closure report on the target release truth branch, green CI for the #836 integration head, and evidence links for E2E/acceptance, adapter dry-run, runtime approvals, accessibility smoke, and Stage-0/final snapshots. Full Sprint 32 milestone closure remains explicitly out of scope for PR #843 while other Sprint 32 P0 lanes remain open.

## Known limitations / blockers

- #835 is closed and PR #842 is merged; this closes the #830-#836 Beta readiness slice only after #836 merges. It does not close the overall Sprint 32 milestone, which still has remaining P0 lanes outside #830-#836 such as #771, #787, #792, #794, and #795.
- Local dirty worktrees are not release truth. Use GitHub PRs/checks, protected branches, and checked-in evidence artifacts.
- Sprint 32 currently targets `dev`; promotion to `main`, RC tagging, or production publication is outside this #836 preparation PR.

## Next safe action

Review and merge PR #843 for #836 release evidence after `docsCheck`, `releaseEvidenceCheck`, and GitHub CI are green. Then close #836 as the Beta readiness release-evidence slice; keep the overall Sprint 32 milestone open until the remaining P0 lanes outside #830-#836 are closed.
