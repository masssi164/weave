# Admin/Operator Handbook

This handbook is for organization owners, admins, and operators responsible for provisioning and running Weave. Normal members should receive an invite/deep link or organization auth URL, complete SSO, and consume effective capability states; they should not configure provider internals.

## Organization setup

The strategic setup contracts are [Organization embedding contract](organization-embedding-contract.md), [Identity provisioning strategy](identity-provisioning-strategy.md), and [Provider replacement and anti-silo contract](provider-replacement-and-anti-silo-contract.md). They are the source for real-organization onboarding, LDAP/AD/OIDC/SAML/SCIM provisioning, mixed provider topologies, adapter replacement, and anti-silo guarantees.

Admin/operator setup owns:

- organization creation and verified domains;
- identity provider configuration;
- provider category selection;
- endpoint URL management and rotation;
- `SecretRef` wiring for provider credentials;
- capability/RBAC profiles and deny-by-default policy;
- provider/tool/agent whitelists;
- readiness, audit, backup/restore, and support bundles.

The current product order remains: provider-neutral Weave suite first, admin portal/IDM/RBAC/readiness/whitelisting second, optional governed Weaver runtime later.

## Identity and Keycloak default

Keycloak is the self-hosted default identity choice for dogfood deployments. The product contract remains provider-neutral: Entra ID, Authentik, Auth0, OIDC/SAML, SCIM, or LDAP-style sources can attach through adapter contracts when supported.

Admins map identity claims, groups, and roles into Weave capability profiles. Unknown roles, groups, or provider states must fail closed. Email addresses are never primary identity keys; immutable provider IDs such as OIDC/SAML issuer+subject, SCIM externalId, Entra object ID, or LDAP/AD objectGUID/objectSid are the mapping anchors.

## Provider selection

Provider categories are first-class product concepts:

- identity/IDM;
- chat;
- files;
- calendar;
- boards/tasks;
- meetings/calls;
- documents/collaboration;
- Weaver runtime (disabled by default until later governed policy work).

Record provider posture as `recommended_self_hosted_default`, `external_existing_provider`, or `managed_cloud_provider`. Provider-specific risk notes belong in admin/operator surfaces, not normal member UX.

The Admin Console setup cockpit is domain-first: each card starts with the Weave domain, selected provider adapter, provider reality level, readiness state, evidence freshness, restart-survival evidence, member impact, and the safe next operator action. Sprint 11 targets `configured_readiness`: the UI can prove selected provider mappings survived backend refresh/restart evidence when the backend reports it, but it must not imply live adapter mutation unless a later `adapter_runtime_verified` or `live_mutation_guarded` level is returned.

Before applying or switching a provider, run a backend dry-run for the selected domain, adapter, and choice model. Apply remains blocked unless the current Admin Console session holds a fresh backend-issued dry-run evidence ref, the backend gates report admin/operator scope, audit sink availability, rollback/archive refs, source/target readiness, export snapshots, loss/conflict handling, and the operator explicitly confirms member impact and rollback consequences. Missing, stale, or client-only/forged dry-run evidence must stop the UI before it calls the apply endpoint.

Rollback decision points are: keep the current adapter active until export/import and rollback evidence pass; archive provider mapping refs before cutover; use only support-safe audit refs in support bundles; and keep the member preview provider-neutral (`available`, `disabled_by_policy`, `not_configured`, `degraded`, `unavailable`, or `coming_later`) during and after the switch.

## Provider URLs and SecretRefs

Provider URLs, credentials, OAuth client secrets, app passwords, signing keys, and bearer tokens must stay out of the member client and support artifacts. Store and display secret handles as `SecretRef` values only. Rotate provider URLs/secrets through admin/operator workflows and audit the change.

## Identity realm dry-run

Use `POST /api/admin/identity/realm/dry-run` before changing Keycloak/OIDC realm state. The request compares an optional `currentState` snapshot with the `desiredState`; if `currentState` is omitted, the backend produces an import/create plan only. This endpoint is dry-run only: it must not mutate Keycloak, OpenTofu/Terraform state, credentials, or member-facing provider configuration.

The desired-state contract covers realm basics, OIDC clients, roles, groups, scopes, claim mappers, redirect origins, and feature mappings. The backend returns deterministic `changes` with `safe`, `risky`, or `destructive` classification plus readiness (`ready`, `degraded`, `policy-blocked`, or `admin-action-required`). Unknown roles, groups, scopes, or feature mappings deny by default and require admin mapping before apply can exist. Destructive removals are policy-blocked in this slice.

Evidence must stay support-safe: no raw provider bodies, provider-internal IDs, credential-bearing URLs, private keys, tokens, or SecretRef payloads. A sanitized sample is checked in at `docs/evidence/identity-realm-dry-run-sample.json`; contract fixtures live under `server/src/test/resources/identity-realm-dry-run/`.

## Identity realm guarded apply

Use `POST /api/admin/identity/realm/apply` only after the #233 dry-run report and #369 effective policy simulation have both been reviewed. The apply endpoint now requires a fresh backend-persisted dry-run id, a support-safe effective policy simulation audit ref, retained-admin proof, rollback/export evidence when risky or destructive changes exist, the audit sink, and the exact confirmation phrase. Live Keycloak mutation is disabled by default and only considered when release/operator configuration explicitly enables `weave.identity.realm.apply.live-apply-enabled=true` with an operator-owned provider runtime (`keycloak-admin-base-url` plus bearer credential sourced from the operator secret layer, never from member input or support evidence).

Apply is unavailable or blocked when any guard fails:

- missing `confirmationPhrase=APPLY WEAVE IDENTITY REALM`;
- no retained immutable owner/admin primary identity key such as `issuer+subject`; the retained key must also be present in desired `lastAdminSubjectRefs` or in a desired break-glass/recovery identity carrying the `owner` or `admin` role, and email addresses are not accepted as recovery keys;
- risky changes without `approveRisky=true` and a support-safe rollback evidence reference;
- destructive changes without `approveDestructive=true`, rollback/restore evidence, provider support for destructive apply, and explicit destructive release/operator configuration; the current Keycloak realm provider reports `destructiveApplyAvailable=false`;
- dry-run blockers remain, including unknown identity inputs, lockout risk, or destructive removals blocked by the dry-run slice.

The audit trail records only support-safe fields and counts: authenticated actor class, realm candidate, dry-run plan ref, decision/result, live-apply enablement, provider configured boolean, change counts, retained-admin count, rollback evidence presence, and mutation-performed status. It must not include raw reason text, rollback payloads, email primary keys, provider internals, tokens, credentials, SecretRef payloads, endpoint URLs, provider ids, or provider response bodies. When live apply is disabled, the accepted decision remains support-safe and returns `guarded-provider-live-apply-disabled` with no provider mutation. If live apply is enabled but the runtime is unavailable, apply blocks before mutation. If live apply is enabled and configured, the adapter proves a minimal Keycloak Admin REST desired-state slice for realm settings, clients, roles, and groups; `providerMutationPerformed=true` is reported only after a successful create/update response, while already-present no-op verification uses `guarded-keycloak-live-apply-noop`. A support-safe accepted-decision fixture is checked in at `server/src/test/resources/identity-realm-apply/guarded-safe-accepted.json`.

## Identity provider readiness in Workspace Health

Workspace Health reads identity/provider readiness from backend-owned facades only. Use `GET /api/admin/identity/readiness` or the embedded `identityProviderReadiness` block on `GET /api/admin/control-plane`; normal members must not call these admin endpoints.

The identity readiness contract is optional/version-skew safe: if an older backend omits it, Admin Console treats identity readiness as `admin-action-required` and fails closed rather than enabling member provider setup. Stable admin states are:

- `ready`;
- `degraded`;
- `policy-blocked`;
- `admin-action-required`;
- `disabled`.

Workspace Health currently renders five operator cards: realm import, OIDC client readiness, roles/groups mapping, login readiness, and policy readiness. Each card must include support-safe remediation and next actions. Do not include OIDC issuer URLs, client IDs, redirect URIs, realm internals, raw provider errors, credentials, tokens, or service endpoints in these cards. Member clients only see product-level capability states (`available`, `disabled_by_policy`, `not_configured`, `degraded`, `unavailable`, or `coming_later`) and never provider setup controls.

## Whitelisting and policies

Weave policy is deny-by-default. Capability profiles should use category-level permissions before low-level adapter details, for example:

- `chat.read`, `chat.send`;
- `files.read`, `files.upload`;
- `calendar.read`, `calendar.manage_events`;
- `boards.read`, `boards.update_task`;
- placeholder Weaver keys only when explicitly gated.

Whitelists restrict which providers, adapters, tools, and later Weaver capabilities are visible to an organization or role. A missing whitelist entry does not grant access.

## Effective policy simulation

Use `POST /api/admin/policies/effective/simulations` before applying identity, realm, or provider policy changes. The endpoint is admin/operator only and simulates the member-visible impact of selected roles, groups, and requested capabilities without mutating provider configuration, realm state, whitelists, or member accounts.

The simulation complements Workspace Health/admin readiness work (#212): readiness explains whether backend-owned provider setup is ready or degraded, while effective policy simulation explains whether known identity inputs would grant, disable, degrade, or policy-block product capabilities for members. It also fits before the identity realm dry-run/apply path (#233): run the realm dry-run to inspect desired realm changes, then run effective policy simulation to preview capability impact before any guarded apply.

Unknown roles, groups, or capabilities fail closed and produce `disabled_by_policy` member states. The response uses only stable member state labels (`available`, `disabled_by_policy`, `not_configured`, `degraded`, `unavailable`, `coming_later`) and admin reason codes; it must not expose email as a primary identity key, raw provider IDs, endpoint URLs, tokens, credentials, SecretRef payloads, or provider internals. Weaver remains disabled by default in this slice; `weaver.enabled` reports `disabled` unless later governed policy work explicitly enables a runtime. A support-safe fixture is checked in at `server/src/test/resources/effective-policy-simulation/admin-operator-preview.json`.

## Readiness and audit

Readiness states must be support-safe and action-oriented. Member contracts encode `available`, `disabled_by_policy`, `not_configured`, `degraded`, `unavailable`, or `coming_later`; admin/operator identity readiness uses `ready`, `degraded`, `policy-blocked`, `admin-action-required`, or `disabled`. Other admin/operator provider views may additionally show `misconfigured`, `sync-pending`, `conflict-quarantined`, `migration-dry-run-required`, or `unsupported`. They must not expose raw downstream bodies, provider-internal IDs, credential-bearing URLs, tokens, cookies, or private keys.

Workspace/Admin Health is the operator control plane for this posture. The client readiness cockpit summarizes overall posture, category health, support-safe evidence, member/admin boundaries, and the next operator action from backend-owned readiness snapshots. Category rows should state member impact and policy state without leaking provider internals; provider adapter evidence remains admin-only.

Audit records should cover admin changes, denied access, provider writes, readiness transitions, SecretRef rotations, mapping-loss events, and support-bundle generation.

## Infra and bootstrap

OpenTofu is the operator-facing infrastructure tool. User-facing workflows and docs should use OpenTofu language unless they are explicitly describing Terraform-compatible internals.

Use the infra tree for local/single-host stack bootstrap, smoke checks, backup/restore, rollback, and support-bundle flows. State-destructive operations require explicit operator confirmation and a backup/restore or rollback path.

## Support bundles

Support bundles must redact secrets, tokens, cookies, private keys, raw provider errors, credential-bearing URLs, generated credentials, and unnecessary provider internals. Include enough sanitized readiness and audit evidence to diagnose operator issues without leaking user or provider data. Sprint 3 adapter readiness evidence is captured in [Sprint 3 Admin readiness evidence](sprint-3-admin-readiness-evidence.md).

## Validation gates

Use the smallest meaningful gate:

```sh
make acceptance-contract
make infra-static
make server-ci
make docs-check
```

Live Stack E2E is available by default on the dedicated self-hosted live runner. Use the GitHub workflow for manual release-candidate evidence; nightly runs should produce acceptance evidence unless a concrete infrastructure blocker is recorded.

The support-safe dogfood realm baseline lives at `server/src/main/resources/identity/weave-realm-baseline.json`. Treat it as desired-state input for dry-run; it is not Terraform/OpenTofu state and intentionally contains no client secrets.
