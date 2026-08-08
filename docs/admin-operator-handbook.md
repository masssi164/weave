# Admin/Operator Handbook

This handbook is for organization owners, admins, and operators responsible for provisioning and running Weave. Normal members should receive an invite/deep link or organization auth URL, complete SSO, and consume effective capability states; they should not configure provider internals.

## Organization setup

The strategic setup contracts are [Organization embedding contract](organization-embedding-contract.md), [Identity provisioning strategy](identity-provisioning-strategy.md), and [Provider replacement and anti-silo contract](provider-replacement-and-anti-silo-contract.md). They are the source for real-organization onboarding, LDAP/AD/OIDC/SAML/SCIM provisioning, mixed provider topologies, adapter replacement, and anti-silo guarantees.

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

Admins map Keycloak claims, groups, and `weave-app` client roles into Weave capability profiles. Unknown roles, groups, or federation states fail closed. Email addresses are never primary identity keys; immutable Keycloak subjects are retained only behind server-owned opaque member and invitation references.

## Provider selection

Provider categories are first-class product concepts:

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

## Keycloak platform-security operations

Static realm settings, clients, scopes, roles, groups, the organization baseline, SMTP, federation, and broker configuration derive from the canonical realm source rendered by infrastructure and imported by Keycloak. The bounded post-import migration owns only FGAP state that import cannot express; its second plan must be empty. Neither operation is exposed as a public provider dry-run/apply API.

The Weave Server owns the audited human lifecycle through server-owned contracts:

- `POST /api/bootstrap/owner-invitation` is available only for an empty realm and requires the mounted bootstrap SecretRef;
- `POST /api/admin/organizations/{organizationId}/invitations` creates or resends invite-first activation;
- `GET /api/admin/organizations/{organizationId}/members` returns opaque member references and bounded cursors;
- `PATCH /api/admin/organizations/{organizationId}/members/{memberRef}` changes the product role or enabled state with `If-Match` and idempotency;
- session revocation and offboarding are separate audited operations and must preserve the last active owner.

The server uses Spring Security's OAuth2 Client support and one named Keycloak administration `RestClient`. Admin credentials, raw Keycloak subjects, invitation ids, tokens, action links, and provider bodies never enter public responses or support evidence. Member passwords are created only in the Keycloak required-action browser flow.

## Platform identity readiness in Workspace Health

Use `GET /api/admin/platform/identity/readiness` or the embedded `platformIdentityReadiness` block on `GET /api/admin/control-plane`. The response identifies `keycloak` as the fixed platform authority and always reports `providerSelectable=false`. Normal member clients have no identity-provider selector or realm-configuration controls.

Readiness is support-safe and covers login, invitations, activation mail, membership projection, session revocation, retained-owner protection, workload-client credentials, and federation/broker posture. LDAP/AD and external OIDC/SAML sources remain Keycloak-managed upstream integrations; their absence must not be presented as an alternative identity-provider choice.

## Whitelisting and policies

Weave policy is deny-by-default. Capability profiles should use category-level permissions before low-level adapter details, for example:

- `chat.read`, `chat.send`;
- `files.read`, `files.upload`;
- `calendar.read`, `calendar.manage_events`;
- `boards.read`, `boards.update_task`;
- `agent-runtime.entitled` only from the configured authoritative Keycloak group; human roles never imply it.

Whitelists restrict which providers and adapters are visible to an organization or role. ARC lifecycle permissions remain admin/operator-only, and a missing whitelist or entitlement never grants runtime or domain access.

## Effective policy simulation

Use `POST /api/admin/policies/effective/simulations` before applying identity, realm, or provider policy changes. The endpoint is admin/operator only and simulates the member-visible impact of selected roles, groups, and requested capabilities without mutating provider configuration, realm state, whitelists, or member accounts.

The simulation complements Workspace Health/admin readiness work (#212): readiness explains whether backend-owned provider setup is ready or degraded, while effective policy simulation explains whether known identity inputs would grant, disable, degrade, or policy-block product capabilities for members. It also fits before the identity realm dry-run/apply path (#233): run the realm dry-run to inspect desired realm changes, then run effective policy simulation to preview capability impact before any guarded apply.

Unknown roles, groups, or capabilities fail closed and produce `disabled_by_policy` member states. The response uses only stable member state labels (`available`, `disabled_by_policy`, `not_configured`, `degraded`, `unavailable`, `coming_later`) and admin reason codes; it must not expose email as a primary identity key, raw provider IDs, endpoint URLs, tokens, credentials, SecretRef payloads, or provider internals. Agent runtime entitlement is never inferred from a human role: only the configured authoritative Keycloak group may derive `agent-runtime.entitled`. A support-safe fixture is checked in at `server/src/test/resources/effective-policy-simulation/admin-operator-preview.json`.

## Readiness and audit

Readiness states must be support-safe and action-oriented. Member contracts encode `available`, `disabled_by_policy`, `not_configured`, `degraded`, `unavailable`, or `coming_later`; admin/operator identity readiness uses `ready`, `degraded`, `policy-blocked`, `admin-action-required`, or `disabled`. Other admin/operator provider views may additionally show `misconfigured`, `sync-pending`, `conflict-quarantined`, `migration-dry-run-required`, or `unsupported`. They must not expose raw downstream bodies, provider-internal IDs, credential-bearing URLs, tokens, cookies, or private keys.

Workspace/Admin Health is the operator control plane for this posture. The client readiness cockpit summarizes overall posture, category health, support-safe evidence, member/admin boundaries, and the next operator action from backend-owned readiness snapshots. Category rows should state member impact and policy state without leaking provider internals; provider adapter evidence remains admin-only.

Audit records should cover admin changes, denied access, provider writes, readiness transitions, SecretRef rotations, mapping-loss events, and support-bundle generation.

## Matrix Chat provider-switch dry-runs

Sprint 15 Matrix Chat provider-switch evidence is review-only. Sprint 18 adds one fixture-only bounded Matrix/Synapse Chat proof for limited target-import apply, cutover validation, rollback cleanup, restore-smoke, audit, and no-unaccounted-data-loss reporting. Operators may run backend-owned dry-runs and inspect Admin Console consequence previews, but production Matrix apply/cutover remains blocked until a separate future gate promotes it. Use only SecretRefs and support-safe object/count evidence; never paste raw homeserver URLs, `mxc://` values, provider tokens, room IDs, access tokens, or downstream diagnostics into requests, issues, support bundles, or release artifacts.

Required review evidence before any future promotion includes consequence counts, supported/lossy/unsupported/manual-review/archive-only/vendor-locked counts, member-impact copy using provider-neutral states, power-level permission-impact decisions, media retention/archive policy, rollback limits, restore-smoke refs, release-claim boundaries, and audit refs. Encrypted Matrix history stays `unsupported`/`coming_later` until a client-side key/export strategy is specified and tested. The detailed Sprint 15 runbook and accessibility evidence template live in [Matrix Chat Sprint 15 dry-run policy](matrix-chat-sprint15-dry-run-policy.md); the Sprint 18 proof boundary lives in [Matrix Chat migration proof boundary](matrix-chat-migration-proof.md).

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
