# Sprint 6 epic closure report

Status: closure evidence for issues #212 and #233 after implementation slices #365, #366, #367, #368, #369, and #370.

## Decision

Both Sprint 6 epics still fit the target picture: Weave remains a provider-neutral organization suite where identity/provider setup is owned by the Organization/Admin Console and normal members only sign in to an already-provisioned workspace. The implementation slices materially satisfy the release-aligned acceptance criteria. No new provider executor, broad member UX, or raw Keycloak/Terraform product logic is needed for v0.1 closure.

Future work should treat the live provider mutation executor, richer Admin Console dashboard composition, SCIM/LDAP/Entra adapters, and real rollback orchestration as follow-on issues. They must depend on the contracts below rather than reopening member-side OIDC setup.

## #212 — Admin-owned OIDC setup and realm import

| Acceptance criterion | Closure evidence |
| --- | --- |
| Role model is documented: owner, admin, member, guest. | `docs/admin-provisioned-first-use.md` documents the four binding human roles. Infrastructure operators act through protected deployment environments rather than a fifth member-token role. The former broad local activation helper was later retired in favor of Keycloak Organization invitations and the constrained persistent dogfood-member operation. |
| Admin-owned setup flow is documented separately from user onboarding. | `docs/admin-provisioned-first-use.md` defines the member/admin boundary, while `docs/admin-operator-handbook.md` is the setup/runbook entry point. Normal member onboarding is limited to an invite/deep link or organization auth URL and SSO. |
| A realm import JSON or generator contract exists for the baseline. | Historical server and OpenTofu fixtures were superseded by canonical ADR 0016. The pinned specification corpus now owns the sole `weave.keycloak-desired-state/v2` baseline, closed environment overlay schema, sanitizer profile and signed evidence schemas; `infra/weave-workspace` only renders and reconciles that authority. |
| `owner`/`admin` can be distinguished from `member`/`guest` in backend contracts. | `IdentityRealmDesiredState`, `KeycloakRealmDryRunProvider`, Workspace Health identity readiness, capability simulation, and guarded apply fixtures all model role/group/capability inputs; unknown roles fail closed and admin/provider endpoints require admin control-plane capabilities. |
| Future admin dashboard work has a clear dependency on this role/setup contract. | Workspace Health/Admin Console must consume `GET /api/admin/identity/readiness`, `GET /api/admin/control-plane`, `/api/admin/identity/realm/dry-run`, `/api/admin/policies/effective/simulations`, and `/api/admin/identity/realm/apply`. It must preserve the role/setup split in `docs/admin-provisioned-first-use.md` and `docs/admin-operator-handbook.md`, showing provider diagnostics only to owner/admin surfaces. |
| User-facing help says users sign in; it does not ask normal users to configure OIDC. | `docs/admin-provisioned-first-use.md`, `docs/user-handbook.md`, architecture tests, and client setup copy keep OIDC/realm/provider setup out of normal member paths; users sign in through the organization endpoint and consume capability states. |

## #233 — Keycloak realm provider dry-run/apply architecture

| Acceptance criterion | Closure evidence |
| --- | --- |
| Inspect the then-current Keycloak Terraform/OpenTofu module. | Historical Sprint 6 evidence inspected the former `02-keycloak-setup` state. ADR 0016 later superseded that executable path with the protected Compose/kcadm desired-state reconciler; this row is retained only as closure history. |
| Define `IdentityRealmProvider` contract. | `server/src/main/java/com/massimotter/weave/backend/identity/realm/IdentityRealmProvider.java` defines `providerKey`, `dryRun`, and `destructiveApplyAvailable=false` by default. |
| Model desired realm spec. | `IdentityRealmDesiredState` models realm basics, clients, roles, groups, scopes, claim mappers, redirect origins, feature mappings, provider warnings, and blockers. |
| Add dry-run/diff/readiness contract. | `KeycloakRealmDryRunProvider` returns deterministic support-safe diffs, readiness checks, warnings/blockers, `destructiveApplyAvailable=false`, and no raw secrets. Contract fixtures cover no-op, create, update, risky, destructive, and invalid states. |
| Owner/admin-only access boundary. | Admin control-plane endpoints require backend admin/provider capabilities. `docs/admin-operator-handbook.md` states normal members must not call identity readiness or realm endpoints. |
| Tests prove no secrets leak and destructive apply is unavailable by default. | `IdentityRealmDryRunFixtureContractTest` checks deterministic support-safe output and `destructiveApplyAvailable=false`; `AdminControlPlaneServiceTest` covers guarded apply, retained-admin protection, rollback evidence gates, mutation-performed=false, and redaction of tokens/emails/provider internals. |

## Sprint 6 child-slice evidence

- #365 / PR #371: Keycloak realm desired-state dry-run provider and fixture matrix.
- #366 / PR #373: identity provider readiness surfaced in Workspace Health/Admin Console contracts.
- #367 / PR #372: repeatable release-candidate readiness evidence check.
- #368 / PR #374: Live Stack E2E failure diagnostics and support-safe evidence hardening.
- #369 / PR #375: effective provider capability policy simulation before apply.
- #370 / PR #376: guarded identity realm apply decision path without live provider mutation.

## Closure rule

Close #212 and #233 as satisfied by the merged Sprint 6 slices plus this closure map. Do not expand them into a live Keycloak executor or member-facing provider setup. Open new follow-up issues only for release-aligned gaps such as Admin Console dashboard composition, additional IdP adapters, SCIM/LDAP reconciliation, rollback artifact orchestration, or an explicitly reviewed provider mutation executor.
