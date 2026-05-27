# Sprint 5 closure report: project readiness foundation

Status: final closure evidence, 2026-05-27.

## Closure scope

Sprint 5 closes the project-readiness foundation for Weave as a provider-neutral organization suite. The sprint moved readiness from aspirational docs into executable backend, admin, and client boundaries: immutable identity subjects, effective policy enforcement, adapter-fit contracts, provider replacement dry-run evidence, and an Admin Console that explains role-specific policy/readiness boundaries without leaking provider internals to members.

This is not a public release publication and not a broad provider marketplace claim. It is the release-candidate foundation that lets the next sprint work from measured readiness instead of optimism-flavored soup.

## Final closure status

- Sprint 5 milestone issue set: #346 through #351.
- Closed before this report: #346, #347, #348, #349, and #350.
- Final closure issue: #351, closed by the PR that adds this report and navigation entry.
- Release posture: `main` is release-capable for the Sprint 5 foundation once this final documentation PR merges with protected checks green.
- Live-stack posture: runtime credentialed Live Stack E2E was not rerun for this closure branch. The documented exception is intentional: Sprint 5 validates provider readiness/control-plane contracts through CI, acceptance mapping, admin/client boundary tests, and support-safe dry-run evidence; credentialed runtime checks remain a release-candidate gate when real credentials are supplied.

## Issue and PR graph

- #346 — Identity-first organization bootstrap and immutable subject model.
  - Closed by PR #352, `feat: identity org bootstrap foundation`.
  - Evidence: backend identity bootstrap contracts, immutable subject model, and audit-safe admin foundation.
- #347 — Enforce effective policy across backend domain facades.
  - Closed by PR #353, `feat: enforce effective workspace policy`.
  - Evidence: backend facade policy enforcement and fail-closed capability states.
- #348 — Domain adapter fit contracts for core product domains.
  - Closed by PR #355, `test: enforce core domain adapter fit contracts`.
  - Evidence: canonical feature/domain adapter-fit contracts for identity, chat, files, calendar, boards/tasks, meetings/calls, documents/collaboration, decisions/evidence, manuals/help, admin health/readiness, and guarded Weaver runtime.
- #349 — Provider replacement dry-run and anti-silo export/delete contracts.
  - Closed by PR #356, `feat: add provider replacement dry-run contract`.
  - Evidence: admin-owned provider replacement dry-run API, lossy-mapping/readiness/lifecycle reports, export/delete expectations, and support-safe audit output.
- #350 — Admin Console policy and provider readiness UX.
  - Closed by PR #357, `feat: show admin policy readiness boundaries`.
  - Evidence: Admin Console role boundaries for owner/admin, operator, and member views; provider replacement dry-run presentation; and member-client architecture tests that block admin/provider control-plane leakage.
- #351 — Project-readiness evidence gate and release-candidate checklist.
  - Closed by this final report PR.

## Frozen readiness contract

Sprint 5 defines "project-ready" for this phase as all of the following:

- All Sprint 5 implementation issues are closed by merged PRs with exactly one release-notes label.
- Protected GitHub checks are green on every Sprint 5 PR before merge.
- `main` has green post-merge CI after each implementation merge before the next closure claim.
- Release evidence tooling remains deterministic: release notes, README markers, docs checks, and scenario mappings are validated by CI/local gates.
- Admin/operator surfaces may inspect provider category readiness, replacement dry-runs, migration lifecycle evidence, and support-safe diagnostics.
- Member surfaces receive only Weave product capabilities and stable states such as ready, disabled, degraded, policy-blocked, or admin setup required.
- Raw provider secrets, tenant URLs, downstream bodies, SecretRefs, raw diagnostics, and admin-only remediation controls stay out of member UX.
- Mixed-provider deployments are valid, but provider swaps are only claimed where a specific migration/dry-run contract proves authorization, lossy mapping, readiness, audit, rollback/restore, export/delete, and stable member impact.
- Weaver remains disabled by default unless governed organization policy enables it; future runtime work must stay opt-in, audited, and capability-whitelisted.

## Mixed-provider/readiness scenario exercised

Sprint 5 exercised the realistic composite-provider case instead of pretending Weave is either fully self-hosted or fully SaaS-only:

- Organization embedding contracts classify identity/IDM, chat, files, calendar, boards/tasks, meetings/calls, documents/collaboration, decisions/evidence, manuals/help, admin health/readiness, and Weaver as provider categories.
- Adapter-fit contracts verify that those categories map through Weave-owned domain contracts before member UX depends on them.
- Provider replacement dry-run evidence verifies a target adapter swap before apply, including lossy mapping, cutover gates, lifecycle expectations, audit publication, export/delete posture, and member impact.
- Admin Console UX exposes that evidence differently by role:
  - owner/admin: configure mappings, whitelist policy, and dry-run/apply through backend admin APIs;
  - operator: inspect readiness and support-safe diagnostics without raw secrets or downstream bodies;
  - member: see only stable capability states and product-safe impact language.

## Evidence snapshot

- PR #352 merged: identity org bootstrap foundation.
- PR #353 merged: effective workspace policy enforcement.
- PR #355 merged: core domain adapter-fit contracts.
- PR #356 merged: provider replacement dry-run contract.
- PR #357 merged: admin policy/readiness boundary UX.
- Post-#356 `main` CI: GitHub Actions run `26494597629` succeeded.
- Post-#357 `main` CI: GitHub Actions run `26495316038` succeeded (`Gradle CI: success`; release-notes label check skipped as expected on `main`).
- Local gate after #357 merge:
  - `./gradlew acceptanceContract releaseEvidenceCheck docsCheck` succeeded.
  - `./gradlew adminCi` succeeded.
  - `./gradlew clientCi` succeeded.
- Project-readiness evidence gate:
  - `tools/project_readiness_evidence_check.py` is now part of `releaseEvidenceCheck`.
  - It validates this closure report, Sprint 5 acceptance mappings for identity/control-plane, effective policy, adapter fit, replacement dry-run, Admin Console UX, operator release evidence, support-safe source assertions, and obvious credential/SecretRef leakage in the checked-in report.
- Local aggregate `./gradlew ci`: blocked by `doctor` because this shell runs JDK 17.0.18; Java 21+ is required. GitHub Actions uses Temurin 21 and remains the authoritative aggregate gate.
- Acceptance contract summary:
  - scenario mapping guard passed;
  - 26 release scenarios mapped;
  - runtime evidence was not collected for this closure branch.
- Client offline contract evidence:
  - offline contract mode passed provider-facade and member boundary checks;
  - live credential checks were skipped as expected without real `WEAVE_TEST_USERNAME` / `WEAVE_TEST_PASSWORD` dart-defines.

## Local gates used during closure

```bash
./gradlew adminCi
./gradlew clientCi
./gradlew acceptanceContract releaseEvidenceCheck docsCheck
./gradlew ci  # blocked locally by doctor: JDK 17.0.18; Java 21+ required
```

## Release-candidate checklist

A v0.1 release-candidate promotion can start only when:

- [ ] all Sprint 5 closure PRs are merged with protected checks green;
- [ ] `main` CI is green after the final Sprint 5 merge;
- [ ] `./gradlew releaseEvidenceCheck` passes with the project-readiness evidence check included;
- [ ] release notes are generated from merged PR metadata, not hand-curated memory;
- [ ] no generated local build outputs, assistant workspace files, raw provider credentials, credential-bearing URLs, tenant secrets, or downstream provider error bodies are committed;
- [ ] credentialed Live Stack E2E is either green with sanitized artifacts or explicitly waived by a release owner for a non-release documentation-only change;
- [ ] support-safe evidence proves member/admin/provider separation for the chosen release slice.

## Live Stack E2E status and exception

Live Stack E2E with real provider credentials was not run in this final documentation branch. That is a documented closure exception, not a product release waiver: credentialed Live Stack E2E remains required before tagging or promoting a v0.1 dogfood release candidate. If it cannot run on the release-candidate head, the release owner must accept a named environment/credential blocker and compensating offline evidence.

## Residual risks and non-goals

- No GitHub release was published by Sprint 5 closure work.
- Credentialed Live Stack E2E was not rerun in this final documentation branch; it remains required for an actual release-candidate promotion when live credentials are available.
- Sprint 5 does not claim generic provider marketplace support. Provider replacement remains contract-scoped and category-specific.
- Sprint 5 does not claim autonomous Weaver writes. Weaver runtime stays disabled by default until governed runtime policy, consent, receipts, audit, and capability controls are mature.
- Sprint 5 does not complete enterprise public documentation; issue #354 remains open for README/enterprise architecture communication.
- Older stale labels such as `status:awaiting-review` on already-merged historical PRs are cleanup noise, not closure blockers.

## Sprint 6 entry criteria

Sprint 6 should start only if the Sprint 5 gates are green on `main`, the release-candidate Live Stack status is explicit, and the first provider slice is scoped narrowly enough to preserve support-safe evidence.

Candidate scope derived from evidence:

Sprint 6 should start from measured gaps, not wishcasting:

- Finish public README/enterprise documentation positioning in #354: product-first Weave, optional opt-in governed Weaver/AI PA, trust, effectiveness, and time savings without autonomy hype.
- Promote credentialed release-candidate evidence: run Live Stack E2E with real credentials, attach sanitized output, and decide whether the v0.1 dogfood candidate can be tagged.
- Deepen admin-owned identity/provider operations from the proven control plane: Keycloak/OIDC realm import/dry-run/apply architecture (#212, #233) and the blocked vertical mapping prototype (#283) only where evidence is ready.
- Continue accessible context-driven workflow and meeting work (#218, #216) while preserving the member/admin/provider boundary frozen by Sprints 3 through 5.
- Keep Weaver work opt-in, governed, audited, capability-whitelisted, and disabled by default until runtime policy proves otherwise.
