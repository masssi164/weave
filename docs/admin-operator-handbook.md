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

The current product order remains: provider-neutral Weave suite first, admin portal/IDM/RBAC/readiness/whitelisting second, optional Agent Runtime Control with Weaver/OpenClaw as a runtime provider.

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
- model provider;
- Agent Runtime Control (entitlement-bound and fail-closed; Weaver/OpenClaw is the first runtime provider).

Record provider posture as `recommended_self_hosted_default`, `external_existing_provider`, or `managed_cloud_provider`. Provider-specific risk notes belong in admin/operator surfaces, not normal member UX.

The Admin Console setup cockpit is domain-first: each card starts with the Weave domain, selected provider adapter, provider reality level, readiness state, evidence freshness, restart-survival evidence, member impact, and the safe next operator action. Sprint 11 targets `configured`: the UI can prove selected provider mappings survived backend refresh/restart evidence when the backend reports it, but it must not imply live adapter mutation unless a later `live_read` or `live_write` level is returned.

Before applying or switching a provider, run a backend dry-run for the selected domain, adapter, and choice model. Apply remains blocked unless the current Admin Console session holds a fresh backend-issued dry-run evidence ref, the backend gates report admin/operator scope, audit sink availability, rollback/archive refs, source/target readiness, export snapshots, loss/conflict handling, and the operator explicitly confirms member impact and rollback consequences. Missing, stale, or client-only/forged dry-run evidence must stop the UI before it calls the apply endpoint.

Boards dry-run counts in the generated continuity report are deterministic estimates derived from the sanitized inventory summary (`Board=max(1, workspaces)`, `Task=max(1, channels + files)`, `Watcher=users`). They are fixture/evidence semantics for consequence review, not proof of a live provider export, and provenance refs must use support-safe provider keys rather than raw source provider input.

For Matrix Chat Sprint 15, the dry-run is review-only: the backend returns consequence counts, member-impact copy, rollback limits, audit refs, and explicit apply blockers, but Matrix apply/cutover remains blocked by default. Operators must follow [Matrix Chat Sprint 15 dry-run policy](matrix-chat-sprint15-dry-run-policy.md): no raw Matrix endpoints, `mxc://` values, tokens, homeserver details, provider internals, or raw diagnostics may enter Admin Console evidence, support bundles, issues, or release artifacts.

Rollback decision points are: keep the current adapter active until export/import and rollback evidence pass; archive provider mapping refs before cutover; use only support-safe audit refs in support bundles; and keep the member preview provider-neutral (`available`, `disabled_by_policy`, `not_configured`, `degraded`, `unavailable`, or `coming_later`) during and after the switch.

For Weaver/OpenClaw model routing, admins select the `model` provider category through the Admin Control Plane, for example `lmstudio` with a `SecretRef`/Credential Broker handle. Conversation enters through OpenClaw's stock `channels.matrix` plugin pointed at the OIDC-gated Weave Matrix facade. The workload-only Spring AI MCP edge currently advertises no tools, resources, or prompts; a domain action can be added only after independent current domain authorization, exact action-bound approval evidence where required, idempotency/reconciliation, and immutable ActionEvidence exist. Members do not configure model endpoints, raw model ids, TLS policy, tokens, southbound provider rooms, or MCP transport details. If the selected model endpoint uses a private development CA such as mkcert, mount the CA certificate into the runtime/container read-only and point the HTTP client trust configuration at it (for curl-based probes, `CURL_CA_BUNDLE=/path/in/container/rootCA.pem`). Do not use `--insecure`, disabled certificate validation, or equivalent flags as final evidence; they are temporary diagnostics only.

## Provider URLs and SecretRefs

Provider URLs, credentials, OAuth client secrets, app passwords, signing keys, and bearer tokens must stay out of the member client and support artifacts. Store and display secret handles as `SecretRef` values only. Rotate provider URLs/secrets through admin/operator workflows and audit the change.

## Documents/Office readiness

Document editing and Office launch are not generally available in v0.1. The current Office provider is a fail-closed `contract_only` readiness surface: members see `coming_later`/unavailable capability state, and admins see support-safe remediation rather than provider internals.

Before an Office adapter can move beyond `contract_only`, the admin/operator readiness record must prove these prerequisites without exposing raw provider data:

- document runtime selected and reachable;
- callback URL configured through a backend-owned route;
- JWT/session secret stored as a `SecretRef`;
- storage binding to the files domain;
- permission model for view/edit/comment/review/form-fill;
- health check and callback verification evidence.

Until those gates are green, launch requests must fail before provider mutation/session creation and must not return credential-bearing URLs, document-server tokens, callback secrets, provider-internal IDs, or raw provider errors.

## Keycloak Identity Ops

Identity Ops in `infra/weave-workspace` is the only planner, reconciler, and verifier for the Keycloak baseline. The product server exposes the read-only, support-safe readiness view at `GET /api/admin/identity/readiness`; it has no realm desired-state, dry-run, or apply API and stores no shadow plan. The removed `/api/admin/identity/realm/dry-run` and `/api/admin/identity/realm/apply` routes are intentionally not retained as compatibility endpoints.

Use the profile-specific Gradle tasks from the repository root:

- `:infra:identityDevPlan`, `:infra:identityDevApply`, `:infra:identityDevVerify`;
- `:infra:identityTestPlan`, `:infra:identityTestApply`, `:infra:identityTestVerify`;
- `:infra:identityProdPlan`, `:infra:identityProdApply`, `:infra:identityProdVerify`.

The tasks load the corresponding `dev`, `test`, or `prod` desired-state profile, call the official Keycloak administration surface through the pinned `kcadm` runtime, and emit support-safe plan/apply/verify evidence. Apply remains an explicit operator action. The server and Admin Console consume only readiness and evidence summaries; they do not reconstruct a competing realm plan.

The canonical human access model uses the organization role groups `/owners`, `/admins`, `/members`, and `/guests`. `/capabilities/weaver` is the sole human Weaver entitlement and is a child of the role-free `/capabilities` group. The realm role `weaver-runtime` is workload-only. Legacy `/weave/*` groups, realm-user group fallbacks, claims, and stored observations are not accepted as entitlement authority.

Evidence must remain support-safe: no raw provider bodies, provider-internal IDs, credential-bearing URLs, private keys, tokens, SecretRef payloads, or reconciliation credentials. Failed verification is a hard blocker; readiness must not infer convergence from a successful plan or apply invocation alone.

## Identity provider readiness in Workspace Health

Workspace Health reads identity/provider readiness from backend-owned facades only. Use `GET /api/admin/identity/readiness` or the embedded `identityProviderReadiness` block on `GET /api/admin/control-plane`; normal members must not call these admin endpoints.

The identity readiness contract is optional/version-skew safe: if an older backend omits it, Admin Console treats identity readiness as `admin-action-required` and fails closed rather than enabling member provider setup. Stable admin states are:

- `ready`;
- `degraded`;
- `policy-blocked`;
- `admin-action-required`;
- `disabled`.

Workspace Health currently renders identity operator cards for realm import, OIDC/SAML federation readiness, SCIM/LDAP/AD-style provisioning source readiness, roles/groups mapping, login readiness, deprovisioning readiness, break-glass readiness, service-principal readiness, and policy readiness. Conceptual SCIM, LDAP, and AD connector states are fixture-backed/`coming_later` unless a separate live connector evidence gate promotes them; the readiness surface still fails closed so admins can see lifecycle risk before inviting members. Deprovisioning cards cover access/session revocation, ownership/content references, retained-admin checks, and audit posture without performing live destructive identity mutations. Break-glass cards require immutable retained owner/admin proof, guarded recovery copy, and support-safe audit refs. Service-principal cards treat non-human actors as scoped identities with expiry/rotation and audit expectations. Each card must include support-safe remediation and next actions. Do not include OIDC issuer URLs, SAML metadata, client IDs, redirect URIs, realm internals, raw provider errors, credentials, tokens, service endpoints, provider payloads, or service-principal secrets in these cards. Member clients only see product-level capability states (`available`, `disabled_by_policy`, `not_configured`, `degraded`, `unavailable`, or `coming_later`) and never provider setup controls.

## Whitelisting and policies

Weave policy is deny-by-default. Capability profiles should use category-level permissions before low-level adapter details, for example:

- `chat.read`, `chat.send`;
- `files.read`, `files.upload`;
- `calendar.read`, `calendar.manage_events`;
- `boards.read`, `boards.update_task`;
- `agent-runtime.entitled` only from exact native Keycloak Organization membership `/capabilities/weaver`; human roles never imply it.

Whitelists restrict which providers and adapters are visible to an organization or role. ARC lifecycle permissions remain admin/operator-only, and a missing whitelist or entitlement never grants runtime or domain access.

## Effective policy simulation

Use `POST /api/admin/policies/effective/simulations` before applying identity, realm, or provider policy changes. The endpoint is admin/operator only and simulates the member-visible impact of selected roles, groups, and requested capabilities without mutating provider configuration, realm state, whitelists, or member accounts.

The simulation complements Workspace Health/admin readiness: readiness explains whether backend-owned provider setup is ready or degraded, while effective policy simulation explains whether known identity inputs would grant, disable, degrade, or policy-block product capabilities for members. Run it alongside the profile-specific Identity Ops plan to preview capability impact before an explicit Identity Ops apply; it never replaces provider verification.

Unknown roles, groups, or capabilities fail closed and produce `disabled_by_policy` member states. The response uses only stable member state labels (`available`, `disabled_by_policy`, `not_configured`, `degraded`, `unavailable`, `coming_later`) and admin reason codes; it must not expose email as a primary identity key, raw provider IDs, endpoint URLs, tokens, credentials, SecretRef payloads, or provider internals. Agent runtime entitlement is never inferred from a human role: only exact native Keycloak Organization membership `/capabilities/weaver` may derive `agent-runtime.entitled`. A support-safe fixture is checked in at `server/src/test/resources/effective-policy-simulation/admin-operator-preview.json`.

## Readiness and audit

Readiness states must be support-safe and action-oriented. Member contracts encode `available`, `disabled_by_policy`, `not_configured`, `degraded`, `unavailable`, or `coming_later`; admin/operator identity readiness uses `ready`, `degraded`, `policy-blocked`, `admin-action-required`, or `disabled`. Other admin/operator provider views may additionally show `misconfigured`, `sync-pending`, `conflict-quarantined`, `migration-dry-run-required`, or `unsupported`. They must not expose raw downstream bodies, provider-internal IDs, credential-bearing URLs, tokens, cookies, or private keys.

Workspace/Admin Health is the operator control plane for this posture. The client readiness cockpit summarizes overall posture, category health, support-safe evidence, member/admin boundaries, and the next operator action from backend-owned readiness snapshots. Category rows should state member impact and policy state without leaking provider internals; provider adapter evidence remains admin-only.

Audit records should cover admin changes, denied access, provider writes, readiness transitions, SecretRef rotations, mapping-loss events, and support-bundle generation.

## Matrix Chat provider-switch dry-runs

Sprint 15 Matrix Chat provider-switch evidence is review-only. Sprint 18 adds one fixture-only bounded Matrix/Synapse Chat proof for limited target-import apply, cutover validation, rollback cleanup, restore-smoke, audit, and no-unaccounted-data-loss reporting. Operators may run backend-owned dry-runs and inspect Admin Console consequence previews, but production Matrix apply/cutover remains blocked until a separate future gate promotes it. Use only SecretRefs and support-safe object/count evidence; never paste raw homeserver URLs, `mxc://` values, provider tokens, room IDs, access tokens, or downstream diagnostics into requests, issues, support bundles, or release artifacts.

Required review evidence before any future promotion includes consequence counts, supported/lossy/unsupported/manual-review/archive-only/vendor-locked counts, member-impact copy using provider-neutral states, power-level permission-impact decisions, media retention/archive policy, rollback limits, restore-smoke refs, release-claim boundaries, and audit refs. Encrypted Matrix history stays `unsupported`/`coming_later` until a client-side key/export strategy is specified and tested. The detailed Sprint 15 runbook and accessibility evidence template live in [Matrix Chat Sprint 15 dry-run policy](matrix-chat-sprint15-dry-run-policy.md); the Sprint 18 proof boundary lives in [Matrix Chat migration proof boundary](matrix-chat-migration-proof.md).

## Infra and bootstrap

Docker Compose is the operator-facing single-host deployment authority. Use the common model with exactly one of the `dev`, `test`, or `prod` runtime profiles; the `dev`, `dogfood`, and `main` Git lanes remain a separate delivery concern. Production uses the digest-pinned `prod` model with protected inputs and separate approval. Keycloak resources are reconciled from the checked-in desired state through rootless one-shot Identity Ops and the matching official `kcadm`, not through application startup or a second infrastructure state engine.

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

The product server may produce a support-safe review plan, but it does not carry a second realm
baseline or mutate Keycloak. The one deployment baseline is the pinned
`weave.keycloak-desired-state/v2` contract from the canonical specification corpus; Compose
renders its closed environment overlay and rootless one-shot Identity Ops reconciles it through
the matching official `kcadm`.
