# Sprint 6 kickoff: release candidate and provider operations

Status: historical kickoff plan, 2026-05-27. The server-side realm-provider direction
described below was later retired without a compatibility API; current baseline
reconciliation is owned exclusively by the profile-specific Infra Identity Ops tasks.

Sprint 6 starts from `main` at `e5aa689` after PR #359 merged the enterprise README readiness work for #354. The milestone is **Sprint 6 — Release Candidate & Provider Operations**.

## Entry truth

- Sprint 5 project-readiness foundation is closed; Sprint 6 owns release-candidate evidence and the first provider-operations slice.
- #354 is closed in the Sprint 6 milestone as readiness/kickoff documentation, not Sprint 5 scope.
- Current `main` checks for `e5aa689`: Gradle CI green; release-notes label check skipped on `main` as expected.
- Live Stack E2E is a release-candidate gate. A current-head manual run was dispatched: [run 26498446059](https://github.com/masssi164/weave/actions/runs/26498446059), tracked by #360.

## Enterprise foundation update

After #360 exposed that an offline-green stack can still fail credentialed product evidence, Sprint 6 now starts with an explicit enterprise release foundation before broadening provider scope. The release lane contract is [Enterprise release foundation](enterprise-release-foundation.md) and the checked contract is `release/enterprise-release-gates.json`.

This update does not turn E2E into an Admin Portal feature. E2E remains validation/evidence; Admin/operator work remains setup, policy, readiness, and support-safe remediation.

## First execution slice

Sprint 6 starts with the narrow provider/ops path:

1. **Enterprise release foundation:** release lanes, machine-checked gate contract, Live Stack manifest, support-safe artifact contract, and waiver semantics wired into `releaseEvidenceCheck`.
2. **RC gate:** #360 — credentialed Live Stack E2E on the release-candidate head. A green sanitized artifact or explicit release-owner waiver is required before any RC promotion/tag.
3. **Admin-owned identity setup:** #212 — keep OIDC/realm setup in owner/admin workflows and keep normal member onboarding to sign-in only.
4. **Historical Keycloak realm provider dry-run:** #233 originally introduced a backend/provisioner contract. That implementation is retired; current Keycloak baseline changes use Infra Identity Ops `plan`/`apply`/`verify`, while the server exposes read-only readiness.

Why this slice: it advances release-candidate readiness and the admin/provider boundary already proven in Sprint 5. It is narrow, fail-closed, and evidence-preserving. It does not broaden member UX, does not claim generic provider marketplace support, and does not make Weaver active by default.

## Explicitly not first slice

- #283 stays blocked until identity/provider dry-run evidence is ready; do not mix boards/OpenProject vertical mapping into the kickoff slice.
- #218 and #216 remain valid product work, but workflow/meeting expansion must not dilute the RC gate or identity/provider-ops scaffold.

## Guardrails

- Do not tag or promote a v0.1 release candidate without #360 green evidence or an explicit waiver.
- Provider credentials, tenant URLs, raw downstream bodies, SecretRefs, and private live logs stay out of member UX, docs examples, PR bodies, and support artifacts.
- Destructive provider apply remains unavailable by default until audit, rollback/restore, last-admin guard, and support-safe evidence are proven.
- Weaver remains opt-in, governed, audited, capability-whitelisted, and disabled by default.
