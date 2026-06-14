# Sprint 19 closure report — Dogfood Workspace Control Room

Date: 2026-06-02

## Governing specs

- Product/spec truth remains pinned by `specs/weave-specs.lock.json` and the local default corpus `../weave-specs`.
- Governing product direction: `docs/product-line-and-weaver-plan.md` keeps Weave as the provider-neutral organization suite first, admin/provider readiness second, and Weaver as an optional governed per-user PA runtime later.
- Sprint delivery maps to GitHub milestone 19 and issues #598, #599, #600, and #601. Evidence is carried by repo files, GitHub PRs/checks, sanitized CI artifacts, and acceptance-contract mappings.

## Scope implemented / evidence status

- #600 Go-live control room / fail-closed claims: release claim control now blocks unsupported go-live claims when Sprint 18 manual assistive-technology evidence is still missing. This closed the Sprint 19 go-live-control issue without pretending the AT blocker is release signoff.
- #599 Cross-domain provider impact before cutover: chat provider impact evidence is represented with cross-domain risk/fallback/export/migration posture, support-safe copy, and release wording that does not claim production cutover readiness.
- #601 Governed workspace assistance: bounded Weaver assistance is governed by signed same-user RuntimeProfile enforcement, consent/scope checks, revoked/expired-token/missing-consent/overbroad-grant denial, approval receipts for write-like tools, and support-safe audit references.
- #598 Space control room dogfoodability: the member-visible channel workspace now acts as a dogfoodable Space control room with Chat, Decisions, Files, Boards, Calendar, Meetings, and governed Weaver scout surfaces tied to one support-safe Space identity and acceptance mapping marker `V01_SPACE_CONTROL_ROOM`.

## Implementation evidence

- Space control room: `client/lib/features/chat/domain/entities/channel_workspace.dart`, `client/lib/features/chat/presentation/chat_room_screen.dart`, `client/lib/features/chat/presentation/providers/channel_workspace_preview_provider.dart`, `client/test/features/chat/channel_workspace_test.dart`, `client/test/features/chat/chat_room_screen_test.dart`, `client/test/live_stack_feature_mapping_test.dart`, `client/test/release_1/ux_release_copy_contract_test.dart`, `client/test/release_1/v0_1_release_spine_contract_test.dart`, `docs/release-v0.1-dogfood-plan.md`, `docs/v0.1-golden-path.md`, `e2e/features/v0_1_dogfood_release.feature`, and `e2e/scenario_mappings.json`.
- Cross-domain provider impact: `docs/product-trust-provider-choice-claim-matrix.md`, `docs/governed-weaver-runtime-security-contract.md`, `docs/evidence/weaver-security-privacy-accessibility-report.md`, release evidence and e2e mapping updates from PR #603.
- Governed Weaver assistance: `server/src/main/java/com/massimotter/weave/backend/weaver/WeaverToolInvocationRequest.java`, `server/src/main/java/com/massimotter/weave/backend/weaver/WeaverToolRegistry.java`, `server/src/test/java/com/massimotter/weave/backend/weaver/WeaverToolRegistryTest.java`, `specs/0007-governed-weaver-runtime/spec.md`, `specs/0007-governed-weaver-runtime/tasks.md`, and `specs/0007-governed-weaver-runtime/traceability.yaml`.
- Go-live claim control: release/accessibility and release-trust claim-control evidence from PR #602, including explicit accounting for the still-missing Sprint 18 manual AT signoff.

## Gates and CI evidence

Evidence classification: the PR-train runs below are **historical evidence** valid for the cited commit/run. Current-head release/public-readiness is not claimed by this report; fresh current-head Live Stack E2E, RC evidence, and the #762 successor blocker for Sprint 18 #591 manual assistive-technology signoff are still required where release claims depend on them.

Local gates recorded during the PR train:

- #600 / #602: release evidence and claim-control gates in PR CI; final `main` CI passed in run `26797477161` on merge commit `5cc2e2ec3d159473eaba6f535b0649b935d553ac`.
- #599 / #603: `./gradlew portabilityContractCheck serverCi adminCi docsCheck --console=plain`, `python3 tools/spec_lint.py`, and scenario-mapping JSON validation; final `main` CI passed in run `26798118252` on merge commit `b6a0a0269ba714ad20480b5e973089ab67d1337d`.
- #601 / #604: `cd server && ./gradlew test --tests 'com.massimotter.weave.backend.weaver.WeaverToolRegistryTest' --console=plain` and broader PR CI; final `main` CI passed in run `26798756120` on merge commit `681bd40ffbe49c405584f13a60a059730907d7e2`.
- #598 / #605: `./gradlew clientCi serverCi acceptanceContract --console=plain`; PR checks passed with `Gradle CI` and `Release Notes Label Check` before merge.
- Final `main` CI after #605: run `26799896715` passed on merge commit `39fcdb7c6841d3ecfe24a22bf2faf5761a5f8aaf` with `Gradle CI` successful.
- Post-report `main` CI at milestone closure: run `26801136050` passed on merge commit `be7be46ef1b65708b177eab14e978b00108ab7b4` with `Gradle CI` successful. This is historical CI evidence valid for that commit, not fresh release/public-readiness evidence.

## Issue DAG final state

- #600 — closed by #602: go-live claims fail closed while manual AT evidence remains unresolved. This was prerequisite claim-control posture for honest Sprint 19 closure.
- #599 — closed after #603: cross-domain provider impact proof is available before any chat-provider cutover claim.
- #601 — closed by #604: governed Weaver assistance remains optional, bounded, policy-derived, support-safe, and fail-closed.
- #598 — closed by #605: Space control room dogfoodability and `V01_SPACE_CONTROL_ROOM` acceptance mapping are merged.

Dependency shape:

1. #600 protects release/readiness claims and prevents false closure language.
2. #599, #601, and #598 are parallel product slices over provider impact, governed assistance, and member control-room UX.
3. #598 lands last because it ties the member-visible control-room surface to the now-governed/non-claiming evidence posture.

## PR / CI / milestone status

- Merged PR: #602 `Fail-close go-live claim control on Sprint 18 AT blocker`, label `release-notes-bugfix`, merge commit `5cc2e2ec3d159473eaba6f535b0649b935d553ac`.
- Merged PR: #603 `feat: prove chat cross-domain provider impact`, label `release-notes-feature`, merge commit `b6a0a0269ba714ad20480b5e973089ab67d1337d`.
- Merged PR: #604 `feat: govern bounded Weaver assistance`, label `release-notes-feature`, merge commit `681bd40ffbe49c405584f13a60a059730907d7e2`.
- Merged PR: #605 `feat: make Space control room dogfoodable`, label `release-notes-feature`, merge commit `39fcdb7c6841d3ecfe24a22bf2faf5761a5f8aaf`.
- Merged PR: #606 `docs: add Sprint 19 closure report`, label `release-notes-skip`, merge commit `be7be46ef1b65708b177eab14e978b00108ab7b4`.
- GitHub milestone 19 is closed with 0 open issues and 4 closed issues after the closure report exists on `origin/main` and post-report CI is green.
- Closure report path: `docs/sprint-19-closure-report.md`.

## Boundaries and non-claims

- No live infrastructure mutation, production release, RC tag, public release publication, provider cutover, or history rewrite was performed.
- The Space control room proves a dogfoodable member workspace route and support-safe state model; it does not claim full Files/Boards/Calendar/Meetings/Decisions provider parity.
- The provider-impact work proves bounded pre-cutover impact posture; it does not claim production migration/cutover readiness.
- Weaver remains optional, disabled by default until organization policy enables it, per-user, auditable, consent/scope-gated, and capability-whitelisted. This sprint does not claim broad autonomous AI availability, unrestricted tools, background agents, raw provider access, or shared-state writes without approval receipts.
- Raw provider tokens, raw provider diagnostics, Matrix/provider URLs, credential-bearing links, and private content remain out of member-facing state and release evidence.
- Sprint 18 manual assistive-technology signoff remains a release blocker; Sprint 19 claim control accounts for it but does not satisfy it.

## Remaining risks / carryovers

- Collect actual manual assistive-technology evidence before any production/public release signoff that depends on it.
- Run or cite fresh credentialed Live Stack E2E when promoting a release candidate; local acceptance mapping for Sprint 19 is not a replacement for live-stack release evidence.
- Continue hardening production-grade Weaver isolation before stronger sandbox/runtime rollout claims.
- Keep provider replacement/cutover as an explicitly approved admin/operator action with fresh evidence, not an implied effect of this sprint.
