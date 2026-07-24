# Weave Monorepo Architecture Alignment

This note records the product-stack boundaries that replaced the older boundary planning model. It complements the root README; when they differ, the current README and executable contracts win.

## Responsibility boundaries

### `client/`

Owns the native user experience and client-side product contracts:

- public OIDC sign-in against Keycloak
- Matrix Native OAuth 2.0 login against the homeserver/MAS stack where the client protocol requires it
- user-facing chat, files, calendar, boards/tasks, meetings, decisions, settings, and health flows
- accessibility, localization, offline/mobile behavior, and widget/architecture tests

The client must not hold provider service tokens, backend actor secrets, LiveKit API secrets, or raw credential-bearing provider URLs.

### `server/`

Owns server-side product APIs and orchestration:

- validate JWTs from Weave clients
- expose Weave product REST APIs and OpenAPI contracts
- own provider facades for files, calendar, boards/tasks, meetings, readiness, audit, and support-safe diagnostics
- enforce authorization, Context/Space boundaries, consent/audit gates, and fail-closed error behavior
- run server-owned workflows that should not live in Flutter

### `infra/`

Owns the runnable environment and operator contract:

- hostnames, TLS, ingress, and service discovery
- Docker Compose profiles own topology and generated runtime config; a protected, idempotent `kcadm` reconciler owns the Keycloak baseline
- local and single-host stack bootstrap for Keycloak, MAS, Synapse, Nextcloud, backend, Caddy, PostgreSQL, and optional providers
- backup, restore smoke, operator checks, support-bundle redaction, and manual live-stack evidence

### `e2e/`

Owns product-language acceptance contracts:

- Gherkin scenarios under `e2e/features/`
- `e2e/scenario_mappings.json` linking scenarios to executable evidence
- sparse live-stack end-to-end gates for critical product contracts

## Active alignment rules

- Treat `client/`, `server/`, `infra/`, `e2e/`, `docs/`, and `release/` as one monorepo release unit.
- Prefer backend-owned facades for product behavior that needs provider orchestration, provider secrets, support-safe diagnostics, audit, or fail-closed behavior.
- Direct client-to-provider protocols are allowed only when they are the correct user protocol and do not bypass product contracts.
- Boards/Tasks is an active v0.1 workspace surface behind backend facade, runtime, authorization, and audit gates.
- OpenProject is the first provider-backed workspace-sync validation path, not the visible product UX and not a direct client dependency.
- Provider writes remain disabled/fail-closed unless a later promotion proves authorization, user consent, audit publication, support-bundle redaction, and rollback behavior.
- Infrastructure docs and workflows must describe the `dev`, `dogfood`, and `main` Compose profiles as the operator contract. Keycloak baseline changes run only through the protected, idempotent `kcadm` reconciler; the server may dry-run desired state but never apply it live.

## Historical gaps that this model closes

Older planning notes predated the current monorepo contracts. Those notes are useful historical context only. Current work should use the paths and product contracts above.

Examples of obsolete assumptions:

- stale native redirect URI defaults such as `weaveapp://login/callback`
- service-specific localhost routes instead of the canonical product gateway plus `api`, `auth`, `matrix`, and `files` origins
- forwarding user bearer tokens directly into every provider as the default backend model
- presenting raw provider UIs as Weave product surfaces
- treating Boards/Tasks as distant or demo-only when it is now gated v0.1 workspace scope

## Good backend-owned use cases

- workspace metadata and capability APIs
- provider readiness and support-safe diagnostic summaries
- files/calendar/boards/tasks facades that need backend authorization or provider secrets
- server-side provisioning hooks for Nextcloud, Keycloak, OpenProject, or later connectors
- audit logging and consent gates for explicit user actions
- future OpenAPI contracts shared by mobile/web clients

## Issue drafts

Archived issue-ready drafts live under `server/docs/issues/`. They are preserved for implementation history and must be checked against the current README, runtime configuration, and Boards workspace contract before reuse.
Any OpenTofu/Terraform language in historical revisions is superseded by the Compose-profile and protected-`kcadm` contract above.
