# Sprint 6 closure report: release-candidate and provider-operations foundation

Status: final closure evidence, 2026-05-27.

## Closure scope

Sprint 6 closes the release-candidate and provider-operations foundation for Weave. The closed scope is deliberately narrower than a public release: the sprint made release evidence repeatable, kept Live Stack E2E as credentialed product evidence, hardened support-safe failure artifacts, and built the first admin-owned identity/provider operations contracts for realm dry-run, readiness, policy simulation, and guarded apply decisions.

This report does **not** publish, tag, or claim a final v0.1 release. It records the evidence state after PR #377 merged and issues #212 and #233 were closed.

Post-closure update: the first later prerelease with exact-candidate signoff is `v0.1.0-rc.2`, published on 2026-05-28. See [v0.1.0-rc.2 release evidence](release-v0.1-rc2-evidence.md) for the release facts; this Sprint 6 report remains historical foundation evidence.

## Final closure status

- Sprint 6 milestone: `Sprint 6 — Release Candidate & Provider Operations`.
- Milestone state at closure audit: open, with `open_issues: 0` and `closed_issues: 10`.
- Final merge commit in this sprint evidence graph: `baca29cbe44ae0bef3079c45d7eb7320ec6c73e8` from PR #377.
- Post-merge `main` CI for `baca29c`: GitHub Actions run `26535015693` completed with result `success`.
- Release posture: Sprint 6 closes the release-candidate/provider-ops foundation. It does not create an RC tag, publish a GitHub release, or approve production promotion.

## Issue and PR graph

| Issue | Closing evidence | State |
| --- | --- | --- |
| #354 — Enterprise documentation architecture and README repositioning | PR #359 repositioned the public README and documentation architecture for Weave as a provider-neutral organization suite with release-candidate evidence boundaries. | Closed. |
| #360 — Sprint 6 RC Live Stack E2E evidence gate | Live Stack E2E run `26509670131` on `82bc7f9ecb160d45298da71b984f0192a10bacd2` completed with result `success` and recorded the `weave-live-stack-acceptance-evidence` artifact. | Closed. |
| #365 — Keycloak realm desired-state dry-run | PR #371 added deterministic identity realm desired-state, dry-run, diff, readiness, and support-safe fixture coverage. | Closed. |
| #366 — Identity provider readiness in Workspace Health | PR #373 exposed identity readiness through Admin Console/Workspace Health contracts without member-facing provider setup leakage. | Closed. |
| #367 — Repeatable RC readiness check | PR #372 added the release readiness checker and fixture coverage for candidate evidence, blocker, and waiver states. | Closed. |
| #368 — Live Stack E2E failure diagnostics | PR #374 hardened failure diagnostics, support-safe manifests, marker summaries, and redacted support-bundle evidence. | Closed. |
| #369 — Effective provider capability policy simulation | PR #375 added admin/operator policy simulation before provider or realm changes apply. | Closed. |
| #370 — Guarded provider apply path | PR #376 added decision-only guarded apply semantics with explicit confirmation, last-admin protection, rollback evidence gates, and support-safe audit counts. | Closed. |
| #212 — Admin-owned OIDC setup and realm import | PR #377 added the docs-only epic closure report mapping the merged Sprint 6 slices to the acceptance criteria. | Closed. |
| #233 — Keycloak realm provider dry-run/apply architecture | PR #377 added the docs-only epic closure report mapping the provider dry-run/apply architecture evidence to the acceptance criteria. | Closed. |

## Credentialed Live Stack and RC readiness status

The credentialed Live Stack E2E gate for #360 was satisfied for commit `82bc7f9ecb160d45298da71b984f0192a10bacd2` by workflow run `26509670131`, which completed with result `success` and produced the named `weave-live-stack-acceptance-evidence` artifact.

That evidence is not the same as a final release approval. The final Sprint 6 merge commit is `baca29cbe44ae0bef3079c45d7eb7320ec6c73e8`; the audited evidence for that commit is post-merge `main` CI run `26535015693`, which completed with result `success`. No credentialed Live Stack E2E rerun on `baca29c` is claimed by this report.

Before tagging or promoting the first real v0.1 release candidate, the release owner still needs exact-candidate readiness evidence: generated/reviewed release notes, sanitized CI summary for the candidate commit, credentialed Live Stack evidence for that same commit or an explicit release-owner waiver, open release-blocker evidence, rollback note, and signoff as described in [Enterprise release foundation](enterprise-release-foundation.md).

## Closed foundation contract

Sprint 6 freezes these release-candidate/provider-ops rules for the next sprint:

- Live Stack E2E is release evidence, not an Admin Portal feature and not a substitute for PR-safe CI.
- Support artifacts stay support-safe: stable marker summaries, manifests, readiness output, and redacted diagnostics rather than raw provider logs or secrets.
- Normal members sign in to an already-provisioned organization and see Weave product capability states, not OIDC, realm, provider, SecretRef, or infra setup.
- Owner/admin/operator surfaces own identity/provider readiness, realm dry-run, policy simulation, guarded apply decisions, audit, rollback evidence, and support-safe remediation.
- Destructive provider mutation remains unavailable by default until explicit executor, rollback/restore, audit, and release-owner evidence exists.
- Weaver remains governed, opt-in, audited, capability-whitelisted, and disabled by default.

## Local and CI evidence used for closure

- GitHub milestone audit: Sprint 6 milestone still open with zero open issues and ten closed issues.
- PR #377 merge audit: merged as `baca29c` and closed #212 and #233 through `docs/sprint-6-epic-closure-report.md`.
- Post-merge `main` CI: run `26535015693` succeeded on `baca29c`.
- Credentialed Live Stack E2E evidence: run `26509670131` succeeded on `82bc7f9` for #360.
- Local gate for this final report branch: `WEAVE_DOCS_VENV=build/docs-venv ./gradlew docsCheck releaseEvidenceCheck`.

## Residual risks and non-goals

- No final public release was published by Sprint 6.
- No RC tag was created by Sprint 6 closure work.
- Credentialed Live Stack E2E is green for `82bc7f9`, not claimed for the final Sprint 6 merge commit `baca29c`.
- The docs-only epic closure for #212/#233 does not add a live Keycloak mutation executor.
- Sprint 6 does not claim broad provider marketplace support, generic migrations, SCIM/LDAP/Entra reconciliation, or autonomous Weaver writes.
- Admin Console dashboard composition and richer operator UX remain follow-up work on top of the Sprint 6 backend/control-plane contracts.
- Release readiness still depends on exact-candidate evidence and release-owner signoff, not on this report alone. The later `v0.1.0-rc.2` prerelease records that evidence separately in [v0.1.0-rc.2 release evidence](release-v0.1-rc2-evidence.md).

## Next-sprint entry criteria for the first real release

The next sprint can start release work only when the candidate is scoped to evidence, not optimism:

- Pick the exact candidate commit on protected `main`.
- Generate or review release notes from merged PR metadata and the exact release-notes labels.
- Run PR-safe CI and keep the sanitized CI summary for the candidate commit.
- Run credentialed Live Stack E2E on the same candidate commit, or record an explicit release-owner waiver with blocker, owner, expiry, scoped gate, and compensating evidence.
- Export current release-blocker evidence and prove no open blocker remains, or record an explicit waiver.
- Run the repeatable release readiness check against the exact candidate version, tag, commit, CI summary, Live Stack evidence, release notes, and blocker evidence.
- Record release-owner signoff with artifact links and rollback notes before creating an RC tag or publishing a prerelease.
- Keep member/admin/provider boundaries intact while adding any Admin Console dashboard, provider executor, or identity-adapter follow-up work.
