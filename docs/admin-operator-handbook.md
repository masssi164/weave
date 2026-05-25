# Admin/Operator Handbook

This handbook is for organization owners, admins, and operators responsible for provisioning and running Weave. Normal members should receive an invite/deep link or organization auth URL, complete SSO, and consume effective capability states; they should not configure provider internals.

## Organization setup

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

Admins map identity claims, groups, and roles into Weave capability profiles. Unknown roles, groups, or provider states must fail closed.

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

## Whitelisting and policies

Weave policy is deny-by-default. Capability profiles should use category-level permissions before low-level adapter details, for example:

- `chat.read`, `chat.send`;
- `files.read`, `files.upload`;
- `calendar.read`, `calendar.manage_events`;
- `boards.read`, `boards.update_task`;
- placeholder Weaver keys only when explicitly gated.

Whitelists restrict which providers, adapters, tools, and later Weaver capabilities are visible to an organization or role. A missing whitelist entry does not grant access.

## Readiness and audit

Readiness states must be support-safe and action-oriented. They can explain that a capability is ready, disabled, degraded, or policy-blocked, but they must not expose raw downstream bodies, provider-internal IDs, credential-bearing URLs, tokens, cookies, or private keys.

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

Use live-stack E2E only when explicitly authorized and runner power/storage budget is available.
