# Sprint 6 epic closure report

Status: historical closure evidence for issues #212 and #233 after implementation
slices #365, #366, #367, #368, #369, and #370. The server-side realm
desired-state implementation described by the original closure was later retired
without compatibility routes. Profile-specific Infra Identity Ops is now the sole
Keycloak baseline planner, reconciler, and verifier.

## Decision

Both Sprint 6 epics still fit the target picture: Weave remains a provider-neutral organization suite where identity/provider setup is owned by the Organization/Admin Console and normal members only sign in to an already-provisioned workspace. The implementation slices materially satisfy the release-aligned acceptance criteria. No new provider executor, broad member UX, or raw Keycloak/Terraform product logic is needed for v0.1 closure.

Future work should treat richer Admin Console dashboard composition,
SCIM/LDAP/Entra adapters, and real rollback orchestration as follow-on issues.
Keycloak baseline mutation stays in protected Infra Identity Ops and must not
reappear as a server executor or member-side OIDC setup.

## #212 — Admin-owned OIDC setup and realm import

| Acceptance criterion | Closure evidence |
| --- | --- |
| Role model is documented: owner, admin, member, guest. | Historical milestone: `docs/admin-provisioned-first-use.md` documents the four binding human roles. The later persistent dogfood-member infrastructure writer was also retired; current dynamic human lifecycle is Server-owned. |
| Admin-owned setup flow is documented separately from user onboarding. | `docs/admin-provisioned-first-use.md` defines the member/admin boundary, while `docs/admin-operator-handbook.md` is the setup/runbook entry point. Normal member onboarding is limited to an invite/deep link or organization auth URL and SSO. |
| A realm import JSON or generator contract exists for the baseline. | Historical server and OpenTofu fixtures were superseded by canonical ADR 0016. The pinned specification corpus now owns the sole `weave.keycloak-desired-state/v2` baseline, closed environment overlay schema, sanitizer profile and signed evidence schemas; `infra/weave-workspace` only renders and reconciles that authority. |
| `owner`/`admin` can be distinguished from `member`/`guest` in backend contracts. | Native Keycloak Organization role groups map exactly to the `weave-app` client roles `owner`, `admin`, `member`, and `guest`; Workspace Health identity readiness and capability simulation remain read-only, unknown inputs fail closed, and admin/provider endpoints require admin control-plane capabilities. |
| Future admin dashboard work has a clear dependency on this role/setup contract. | Workspace Health/Admin Console consumes `GET /api/admin/identity/readiness`, `GET /api/admin/control-plane`, and `/api/admin/policies/effective/simulations`. Realm planning, apply, and verify are protected Infra Identity Ops tasks and are not Admin Console or server routes. |
| User-facing help says users sign in; it does not ask normal users to configure OIDC. | `docs/admin-provisioned-first-use.md`, `docs/user-handbook.md`, architecture tests, and client setup copy keep OIDC/realm/provider setup out of normal member paths; users sign in through the organization endpoint and consume capability states. |

## #233 — Historical Keycloak realm provider dry-run/apply architecture

| Acceptance criterion | Closure evidence |
| --- | --- |
| Inspect the then-current Keycloak Terraform/OpenTofu module. | Historical Sprint 6 evidence inspected the former `02-keycloak-setup` state. ADR 0016 later superseded that executable path with the protected Compose/kcadm desired-state reconciler; this row is retained only as closure history. |
| Define one desired-state owner. | The former `IdentityRealmProvider` and server-side shadow model are removed. `infra/weave-workspace/keycloak/identity_ops.py` and the profile-specific Gradle tasks are the single implementation authority. |
| Model desired realm spec. | The checked-in `weave.keycloak-desired-state/v2` contract models realm basics, clients, workload roles, native Organization role/capability groups, scopes, redirect origins, and SecretRef boundaries. |
| Add plan/apply/verify contract. | Identity Ops produces deterministic support-safe plans, converges its declared managed surface, requires an empty second plan, and verifies independently. Ordinary apply cannot infer destructive recovery or implicit secret rotation. |
| Owner/admin-only access boundary. | Protected deployment environments authorize Identity Ops apply. The product server exposes only read-only identity readiness; normal members and Admin Console clients cannot mutate the realm through Weave APIs. |
| Tests prove no secrets leak and obsolete routes stay absent. | `identity_ops_contract_test.py` covers plan/apply/verify and secret redaction. `AdminControlPlaneControllerTest.identityDesiredStateRoutesAreRemovedInFavorOfProtectedIdentityOps` proves both former mutation paths return `404`. |

## Sprint 6 child-slice evidence

- #365 / PR #371: Keycloak realm desired-state dry-run provider and fixture matrix.
- #366 / PR #373: identity provider readiness surfaced in Workspace Health/Admin Console contracts.
- #367 / PR #372: repeatable release-candidate readiness evidence check.
- #368 / PR #374: Live Stack E2E failure diagnostics and support-safe evidence hardening.
- #369 / PR #375: effective provider capability policy simulation before apply.
- #370 / PR #376: guarded identity realm apply decision path without live provider mutation.

## Closure rule

Issues #212 and #233 remain historically closed, but their former server-side
realm-provider implementation is not a supported architecture. Do not restore it.
Future identity changes extend the canonical spec and Infra Identity Ops; the
server may consume only support-safe readiness and normalized authorization
results.
