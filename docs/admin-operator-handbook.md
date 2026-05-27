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

## Provider URLs and SecretRefs

Provider URLs, credentials, OAuth client secrets, app passwords, signing keys, and bearer tokens must stay out of the member client and support artifacts. Store and display secret handles as `SecretRef` values only. Rotate provider URLs/secrets through admin/operator workflows and audit the change.

## Identity realm dry-run

Use `POST /api/admin/identity/realm/dry-run` before changing Keycloak/OIDC realm state. The request compares an optional `currentState` snapshot with the `desiredState`; if `currentState` is omitted, the backend produces an import/create plan only. This endpoint is dry-run only: it must not mutate Keycloak, OpenTofu/Terraform state, credentials, or member-facing provider configuration.

The desired-state contract covers realm basics, OIDC clients, roles, groups, scopes, claim mappers, redirect origins, and feature mappings. The backend returns deterministic `changes` with `safe`, `risky`, or `destructive` classification plus readiness (`ready`, `degraded`, `policy-blocked`, or `admin-action-required`). Unknown roles, groups, scopes, or feature mappings deny by default and require admin mapping before apply can exist. Destructive removals are policy-blocked in this slice.

Evidence must stay support-safe: no raw provider bodies, provider-internal IDs, credential-bearing URLs, private keys, tokens, or SecretRef payloads. A sanitized sample is checked in at `docs/evidence/identity-realm-dry-run-sample.json`; contract fixtures live under `server/src/test/resources/identity-realm-dry-run/`.

## Whitelisting and policies

Weave policy is deny-by-default. Capability profiles should use category-level permissions before low-level adapter details, for example:

- `chat.read`, `chat.send`;
- `files.read`, `files.upload`;
- `calendar.read`, `calendar.manage_events`;
- `boards.read`, `boards.update_task`;
- placeholder Weaver keys only when explicitly gated.

Whitelists restrict which providers, adapters, tools, and later Weaver capabilities are visible to an organization or role. A missing whitelist entry does not grant access.

## Readiness and audit

Readiness states must be support-safe and action-oriented. Member contracts encode `ready`, `disabled`, `degraded`, or `policy-blocked`; admin/operator views may additionally show `admin-setup-required`, `misconfigured`, `sync-pending`, `conflict-quarantined`, `migration-dry-run-required`, or `unsupported`. They must not expose raw downstream bodies, provider-internal IDs, credential-bearing URLs, tokens, cookies, or private keys.

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
