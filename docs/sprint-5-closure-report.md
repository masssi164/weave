# Sprint 5 closure report: project readiness foundation

Status: final closure evidence, 2026-05-27.

## Closure scope

Sprint 5 closes the project-readiness foundation for Weave v0.1 dogfood production. The closed scope is intentionally organization-first: identity bootstrap, immutable subjects, effective policy enforcement, provider-category fit, replacement dry-run/export/delete contracts, and an Admin Console readiness UX that keeps provider configuration in admin/operator surfaces instead of member paths.

Sprint 5 does not make Weaver autonomous, does not publish a GitHub release, and does not claim a generic provider marketplace. It makes the current product line release-capable enough to enter Sprint 6 from evidence rather than optimism.

## Final closure status

- Milestone: `Sprint 5 — Project Readiness Foundation`.
- Current completed implementation issues before this final report: #346, #347, #348, #349, and #350.
- Final evidence issue: #351, closed by the PR that adds this report once protected checks pass.
- Post-#349 `main` CI: GitHub Actions run `26494597629` succeeded on `c1128667a2711b57af21c7560f526e8b7db280bf`.
- Post-#350 `main` CI: GitHub Actions run `26495316038` is the release-capability verification run for `0a5adee610cf2b562aa825fdc67a9e8ecbf0419c`; do not close #351 until it is green or an explicit exception is recorded.
- Final #351 PR CI and the post-merge `main` CI remain mandatory before the milestone is considered closed.

## Issue and PR graph

| Sprint 5 issue | Closing PR | Evidence summary | State |
| --- | --- | --- | --- |
| #346 — Identity-first organization bootstrap and immutable subject model | #352 `feat: identity org bootstrap foundation` | Added organization bootstrap/subject model foundation with server/identity evidence and release label `release-notes-feature`. | Closed 2026-05-27. |
| #347 — Enforce effective policy across backend domain facades | #353 `feat: enforce effective workspace policy` | Backend domain facades now enforce effective workspace policy and fail closed for unauthorized capability paths. | Closed 2026-05-27. |
| #348 — Domain adapter fit contracts for core product domains | #355 `test: enforce core domain adapter fit contracts` | Contract tests cover adapter fit, canonical models, provider-facade boundaries, and provider-neutral member behavior. | Closed 2026-05-27. |
| #349 — Provider replacement dry-run and anti-silo export/delete contracts | #356 `feat: add provider replacement dry-run contract` | Added replacement dry-run, lossy mapping, lifecycle/export/delete, readiness, and rollback/restore expectations through backend/admin contracts. | Closed 2026-05-27. |
| #350 — Admin Console policy and provider readiness UX | #357 `feat: show admin policy readiness boundaries` | Added Admin Console role boundaries, provider readiness/replacement dry-run UX, SecretRef-only support-safe handling, and a Flutter member boundary architecture test. | Closed 2026-05-27. |
| #351 — Project-readiness evidence gate and release-candidate checklist | Final closure PR for this report | Lists the Sprint 5 graph, release notes evidence, green-gate expectations, live-evidence exception, residual risks, and Sprint 6 entry criteria. | Closes with this report. |

## Frozen readiness contract

Sprint 5 freezes these rules for the next release-candidate pass:

- Identity bootstrap and immutable subject identity are organization control-plane responsibilities, not per-client setup shortcuts.
- Effective policy is enforced by backend domain facades before provider adapters are reached.
- Provider adapters must prove fit against canonical Weave domain contracts before becoming release candidates.
- Replacement dry-runs must be support-safe: readiness state, lossy mapping notes, export/delete expectations, rollback/restore gates, and member-impact states are exposed without raw secrets or downstream error bodies.
- Admin Console owner/admin views may configure provider categories, policy, whitelists, readiness tests, and replacement dry-runs.
- Operators may inspect support-safe readiness/audit evidence.
- Members see stable product capability states only: `usable`, `disabled`, `degraded`, or `policy-blocked`.
- Normal member clients must not call admin control-plane endpoints, optional provider SDKs, raw provider URLs, provider secrets, SecretRefs, or provider replacement diagnostics directly.

## Evidence snapshot

| Evidence | Run / artifact | Result |
| --- | --- | --- |
| Post-#349 main CI | GitHub Actions `26494597629` on `c1128667a2711b57af21c7560f526e8b7db280bf` | Success. |
| Post-#350 main CI | GitHub Actions `26495316038` on `0a5adee610cf2b562aa825fdc67a9e8ecbf0419c` | Pending at report authoring; must be success or explicitly excepted before #351 closes. |
| #350 PR CI | PR #357 required checks on `720f0c9566ddb6d3987cb19aef5650932f1335b4` | Gradle CI, release-notes label check, and Copilot check completed successfully before merge. |
| Local #350 admin gate | `cd admin-console && npm run ci` and `./gradlew adminCi` | Passed: 2 test files, 10 tests; Vite/TypeScript build passed. |
| Local #350 client gate | `./gradlew clientCi` | Passed in `1m 32s`; offline contract mode passed 5 checks and skipped live credential tests as expected. |
| Acceptance mapping | `./gradlew acceptanceContract` | Passed; `V01_ADMIN_CONSOLE_MVP`, identity bootstrap, effective policy, adapter fit, replacement dry-run, and operator release path markers are mapped. |
| Release notes evidence | `GH_TOKEN=… python3 tools/release_notes_generate.py --repo masssi164/weave --since 2026-05-27T00:00:00Z --output build/release-notes/sprint-5-unreleased.md` | Generated review artifact from merged Sprint 5 PR labels; #352, #353, #355, #356, and #357 all carry exactly one `release-notes-feature` label. |
| Local aggregate attempt | `./gradlew ci` | Blocked locally by Java 17: `Doctor found problems: Java 21 or newer is required`; GitHub Actions uses Temurin 21 and remains the authoritative aggregate gate. |

## Release-candidate checklist

Before a Sprint 5 release candidate can be tagged:

- [ ] Final #351 PR has exactly one release-notes label and green protected checks.
- [ ] Post-#351 merge `main` CI is green.
- [ ] `./gradlew releaseEvidenceCheck` has passed locally or in CI for the release-draft path.
- [ ] Release notes are generated from merged PR metadata, not hand-curated memory.
- [ ] Support bundles and evidence artifacts expose stable codes, counts, states, and SecretRef handles only.
- [ ] No generated local build outputs, assistant workspace files, raw provider credentials, tenant URLs with secrets, or downstream provider error bodies are committed.
- [ ] Live Stack E2E is either run on the release-candidate head or has an explicit release-gate exception accepted by the owner.

## Live Stack E2E status and exception

Live Stack E2E with real provider credentials was not run during this final local closure pass. That is an explicit Sprint 5 closure exception, not a hidden pass. The PR-safe evidence path is offline contract validation plus GitHub Actions aggregate CI. A release candidate must either run the Live Stack E2E workflow against the final `main` head or carry an owner-accepted exception that names the unavailable credential/environment blocker and the compensating offline evidence.

## Residual risks and non-goals

- The Admin Console now exposes readiness and replacement dry-run UX, but production-grade account lifecycle operations still need release-candidate validation against the live stack.
- Provider replacement is contract-backed dry-run evidence, not a broad marketplace or one-click migration claim.
- Anti-silo export/delete expectations are specified as backend/admin contracts; every real provider still needs adapter-specific evidence before being promoted.
- Java 21 is mandatory for the aggregate local `ci` gate; local Java 17 shells will fail `doctor` before implementation gates run.
- Sprint 5 does not publish a GitHub release, change production infrastructure automatically, or make Weaver a write-capable autonomous runtime.

## Sprint 6 entry criteria

Sprint 6 should start only after the final #351 PR and post-merge `main` CI are green. Candidate scope should be evidence-led:

1. Promote the first real provider slice from these contracts, with live readiness, export/delete, rollback/restore, and support-bundle proof.
2. Run the release-candidate Live Stack E2E path or resolve the recorded environment blocker.
3. Convert Admin Console readiness into operator runbooks and support flows.
4. Keep normal member UX provider-neutral while adding only proven, policy-owned capability states.
5. Keep #283 deferred unless it is re-scoped as the first provider slice after the Sprint 5 foundation gates are green.
