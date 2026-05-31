# Sprint 11 Closure Report — Provider Reality & Domain Adapter Foundation

## Closure verdict

Sprint 11 is closed as a provider-reality foundation sprint. The sprint moved Weave from aspirational provider claims toward explicit reality levels, persisted control-plane evidence, guarded provider apply paths, and honest unavailable states for domains that are not yet release-ready.

This report is not a new production-release claim. It records the Sprint 11 integration state on `main` after all Sprint 11 PRs merged and final CI passed.

## Governing scope

Sprint 11 is governed by GitHub milestone 11 and issues #480–#490 plus program umbrella #481. The sprint objective was to make release claims fail closed unless backed by executable/provider evidence, and to harden domain adapters before further Weaver expansion.

## Issue DAG final state

- #480 Accessibility release waiver replacement — closed by Sprint 11 evidence-gate mapping and release evidence work.
- #482 Provider reality levels — closed by exposing provider reality levels and failing closed on overclaims.
- #483 Control-plane persistence — closed by persisting provider selections, migration evidence, org bootstrap, and audit gates.
- #484 Keycloak guarded apply — closed by guarded live apply behind persisted dry-run and rollback evidence.
- #485 Nextcloud Files/Calendar hardening — closed by hardened adapter behavior and release-quality failure handling.
- #486 OpenProject Boards hardening — closed by safer write handling, migration evidence, and unsupported-action honesty.
- #487 Office honest unavailable state — closed by enforcing honest unavailable posture instead of overclaiming Office readiness.
- #488 Admin provider setup UX — closed by domain-first setup/switching evidence gates.
- #489 E2E/accessibility evidence mapping — closed by mapping provider-reality release evidence gates.
- #490 Matrix Chat readiness — closed by locking Matrix Chat as a real release provider path with redacted readiness failures and E2EE-safe evidence.
- #481 Program umbrella — closes after this closure report lands, final `main` CI is green, and milestone 11 is closed.

## Merged PR order and evidence

- #495 `feat: expose provider reality levels` — merged 2026-05-31T13:04:28Z.
- #494 `feat: persist control plane evidence` — merged 2026-05-31T13:16:12Z.
- #509 `feat(server): guard Keycloak realm apply` — merged 2026-05-31T13:23:50Z.
- #492 `fix: harden Nextcloud files and calendar adapters` — merged 2026-05-31T13:30:37Z.
- #493 `fix: harden OpenProject boards writes` — merged 2026-05-31T13:37:23Z.
- #496 `test: map Sprint 11 release evidence gates` — merged 2026-05-31T13:44:26Z.
- #498 `feat: gate admin provider setup evidence` — merged 2026-05-31T13:51:02Z.
- #491 `fix: keep office launch honestly unavailable` — merged 2026-05-31T14:02:30Z.
- #497 `fix: redact Matrix chat readiness failures` — merged 2026-05-31T14:10:33Z.

## Evidence gates

GitHub protected-branch gates were green for each merged PR:

- Required check: `Gradle CI` — PASS.
- Required check: `Release Notes Label Check` — PASS or SKIPPED where the workflow intentionally skipped the non-applicable event copy while the required run passed.
- Required conversation resolution: PASS after Copilot review threads were resolved under the documented fallback policy that Copilot is not a hard reviewer while premium review capacity is exhausted.

Final post-merge `main` evidence:

- Final `main` commit after Sprint 11 integration: `6bb87617c7c445a51133aa38d8a0e81ad4ef1800`.
- Final `main` CI: run `26714890354` (`CI`) — PASS.
- Open PRs after cleanup check: 0.

## Release and product-readiness impact

Sprint 11 improves release honesty rather than expanding public readiness claims. The release posture after Sprint 11 is:

- Provider reality levels are explicit and can distinguish release-ready, guarded, unavailable, and evidence-only states.
- Domain adapter claims are more conservative and fail closed when evidence is missing or stale.
- Office remains honestly unavailable rather than overclaimed.
- Matrix Chat readiness evidence is redacted and E2EE-safe.
- Nextcloud, OpenProject, Keycloak, and admin provider setup paths have stronger persistence, dry-run, rollback, and write-safety evidence.

The next release is intentionally left to Sprint 12 after Sprint 12 scope is fully integrated and release gates are green.

## Closure state

- Milestone 11: ready to close after #481 is closed.
- Remaining Sprint 11 blockers: none known.
- Next work: Sprint 12 execution and next-release completion under the `weave-co-leader` orchestrator.
