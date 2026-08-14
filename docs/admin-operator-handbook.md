# Admin/Operator Handbook

This handbook is for organization owners, admins, and operators responsible for provisioning and running Weave. Normal members should receive an invite/deep link or organization auth URL, complete SSO, and consume effective capability states; they should not configure provider internals.

## Organization setup

The strategic setup contracts are [Organization embedding contract](organization-embedding-contract.md), [Identity provisioning strategy](identity-provisioning-strategy.md), [Keycloak realm lifecycle](architecture/keycloak-realm-lifecycle.md), and [Provider replacement and anti-silo contract](provider-replacement-and-anti-silo-contract.md). They are the source for real-organization onboarding, LDAP/AD/OIDC/SAML/SCIM provisioning, static realm ownership, mixed provider topologies, adapter replacement, and anti-silo guarantees.

Admin/operator setup owns:

- organization creation and verified domains;
- Keycloak platform-security configuration and upstream federation/brokering;
- provider category selection;
- endpoint URL management and rotation;
- `SecretRef` wiring for provider credentials;
- capability/RBAC profiles and deny-by-default policy;
- provider/tool/agent whitelists;
- readiness, audit, backup/restore, and support bundles.

The current product order remains: provider-neutral Weave suite first, admin portal/IDM/RBAC/readiness/whitelisting second, optional Agent Runtime Control with Weaver/OpenClaw as a runtime provider.

## Identity and Keycloak default

Keycloak is the fixed platform identity authority for the current Weave architecture. It owns human authentication, organizations, roles, groups, invitations, required actions, sessions, workload clients, and upstream federation/brokering. LDAP/AD user federation and Entra/Auth0/Authentik-style OIDC or SAML sources attach to Keycloak; they do not replace a runtime-selectable Weave identity provider.

Static IAM topology is deployment-owned. The normal Server runtime does not create or repair realms, first-party clients, scopes, roles, organization groups, protocol mappers, required actions, authentication flows, or FGAP structure. Server owns only bounded dynamic human lifecycle after static IAM has been qualified.

## Provider selection

Provider categories are first-class product concepts: chat, files, calendar, boards/tasks, meetings/calls, documents/collaboration, model provider, and Agent Runtime Control. Record provider posture as `recommended_self_hosted_default`, `external_existing_provider`, or `managed_cloud_provider`. Provider-specific risk notes belong in admin/operator surfaces, not normal member UX.

Before applying or switching a provider, run the backend dry-run for the selected domain and adapter. Apply remains blocked unless the current session holds fresh backend-issued evidence and all consequence, rollback, readiness, conflict, and audit gates pass.

## Provider URLs and SecretRefs

Provider URLs, credentials, OAuth client secrets, app passwords, signing keys, and bearer tokens must stay out of the member client and support artifacts. Store and display secret handles as `SecretRef` values only. Rotate provider URLs/secrets through admin/operator workflows and audit the change.

## Keycloak platform-security operations

The canonical static IAM source is `infra/weave-workspace/keycloak`. It is rendered into an environment-specific, secret-free `RealmRepresentation` from the candidate semantic realm identity plus reviewed environment coordinates and environment-owned public JWKS.

Candidate identity binds only:

- `semanticRealmSourceDigest`;
- `migrationDefinitionDigest`.

Environment evidence additionally binds:

- `overlayDigest`;
- `renderedRealmDigest`;
- `semanticReadbackDigest`.

A concrete environment `realm.json` is generated deployment output and must never become an independently maintained source.

For resettable dogfood or a disposable E2E namespace, startup import creates the
realm from the checked-in projection. Dogfood identity state is development
state and may be recreated by `dogfoodReset`; it does not require a backup or
versioned migration. Any future production realm lifecycle requires a separate
production-hardening ADR before it becomes active.

Every qualified migration must finish with semantic readback, an empty second plan, deletion of temporary bootstrap authority, negative readback proving that authority is gone, and support-safe receipt evidence. Ordinary Server startup does not reconcile static IAM.

The Weave Server owns the audited human lifecycle through server-owned contracts:

- owner/member invitations and resend/revoke lifecycle;
- required-action browser activation;
- organization membership;
- product-role group membership;
- Weaver entitlement group membership;
- suspend/resume;
- session revocation;
- offboarding with retained-owner protection.

Admin credentials, raw Keycloak subjects, invitation ids, tokens, action links, private JWK material, SecretRef payloads, and raw provider bodies never enter public responses or support evidence.

## Platform identity readiness in Workspace Health

Use `GET /api/admin/platform/identity/readiness` or the embedded `platformIdentityReadiness` block on `GET /api/admin/control-plane`. The response identifies `keycloak` as the fixed platform authority and reports `providerSelectable=false`. Normal member clients have no identity-provider selector or realm-configuration controls.

Readiness is support-safe and covers login, invitations, activation mail, membership projection, session revocation, retained-owner protection, workload-client credentials, and federation/broker posture. LDAP/AD and external OIDC/SAML sources remain Keycloak-managed upstream integrations; their absence must not be presented as an alternative identity-provider choice.

Workspace/Admin Health is the operator control plane for this posture. The client readiness cockpit summarizes overall posture, category health, support-safe evidence, member/admin boundaries, and the next operator action from backend-owned readiness snapshots. Category rows state member impact and policy state without leaking provider internals; provider adapter evidence remains admin-only.

## Whitelisting and policies

Weave policy is deny-by-default. Capability profiles should use category-level permissions before low-level adapter details. Agent runtime entitlement is never inferred from a human role: only the configured authoritative Keycloak group may derive `agent-runtime.entitled`.

## Effective policy simulation

Use `POST /api/admin/policies/effective/simulations` before applying identity or provider policy changes. The endpoint is admin/operator only and simulates the member-visible impact of known roles, groups, and requested capabilities without mutating provider configuration, realm state, whitelists, or member accounts.

Unknown roles, groups, or capabilities fail closed. Responses must not expose email as a primary identity key, raw provider IDs, endpoint URLs, tokens, credentials, SecretRef payloads, or provider internals.

## Readiness and audit

Readiness states must be support-safe and action-oriented. Audit records should cover admin changes, denied access, provider writes, readiness transitions, SecretRef rotations, mapping-loss events, static IAM migration receipts, and support-bundle generation.

## Infra and bootstrap

Use the profile-driven Compose tree for local/single-host bootstrap, smoke checks,
and support bundles. `devUp`/`devDown` manage local dependencies;
`dogfoodUp`/`dogfoodDown` manage the test stack; `dogfoodReset` deliberately
recreates its three session volumes while preserving host-managed TLS. Backup,
restore, rollback, and persistent realm migration are future production concerns,
not development prerequisites.

## Support bundles

Support bundles must redact secrets, tokens, cookies, private keys, raw provider errors, credential-bearing URLs, generated credentials, and unnecessary provider internals. Include enough sanitized readiness and audit evidence to diagnose operator issues without leaking user or provider data.

## Validation gates

Use the smallest meaningful gate during development, but release-impacting IAM changes must pass the full root CI and environment-evidence contracts:

```sh
make acceptance-contract
make infra-static
make server-ci
make docs-check
```

The authoritative realm lifecycle is documented in [Keycloak realm lifecycle](architecture/keycloak-realm-lifecycle.md). No server-resource realm baseline file, Identity Ops reconciler, or runtime repair path is an active authority.
