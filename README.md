# Weave Backend

[![CI](https://github.com/masssi164/weave-backend/actions/workflows/ci.yml/badge.svg)](https://github.com/masssi164/weave-backend/actions/workflows/ci.yml)

`weave-backend` is the Spring Boot product API for Weave. It keeps active client contracts small and supportable, avoids pushing backend-owned secrets or raw downstream protocols into clients, and leaves room for the broader Weave vision: accessible workspaces, open operator control, data sovereignty, and a later Weaver intelligence layer for assistants, agents, automation, and connectors.

The backend is intentionally **not** a generic proxy for Matrix, Nextcloud, or Keycloak. Flutter can use native OIDC/PKCE and Matrix client flows where that is the right protocol boundary. This service owns the workflows that benefit from a server-side product layer: normalized Weave APIs, stable error envelopes, release-readiness checks, backend-owned integrations, and orchestration that should not live in the app.

## Active product/API scope

The backend is an active product facade for the MVP collaboration surfaces. Some runtime slices remain feature-gated while contracts, auth, accessibility, and operator validation mature. The backend currently provides:

- public health and platform bootstrap endpoints for gateway and smoke checks
- first-party JWT issuer, audience, client, and `weave:workspace` scope validation
- a stable `/api/me` caller snapshot for contract testing
- product profile facade endpoints at `GET /api/profile`, `PATCH /api/profile`, and `GET /api/profile/sync-status`
- first-run onboarding status at `/api/onboarding/status`
- workspace capability and release-readiness snapshots at `/api/workspace/capabilities` and `/api/workspace/release-readiness`
- Nextcloud-backed Files facade endpoints when a backend-owned actor is configured; otherwise they fail closed
- Calendar facade endpoints mapped to backend-owned Nextcloud CalDAV workspace/team/channel collections while flexible Context/Space scheduling evolves; unsafe private-personal calendar templates fail closed
- secret-free Calendar client setup metadata at `GET /api/calendar/client-setup` for feature-gated native-client setup; it does not return credentials or generate profiles yet
- internal Audit/Consent seam for future connector/assistant writes, consent grant/revocation events, support-safe redaction, and fail-closed missing-audit behavior; it does not enable live provider writes
- OpenAPI JSON at `/v3/api-docs`
- Actuator health/info endpoints, Gradle wrapper, Dockerfile, and GitHub Actions CI

The current backend does **not** yet claim a complete Teams/Slack replacement, end-user credential brokering, full Matrix/Nextcloud provisioning automation, recurrence-rich calendar UX, sharing/move policy, live Boards/Tasks provider runtime, or the later Weaver intelligence layer. Those remain product-roadmap items behind explicit contracts. The hidden Boards/Tasks preview contract is documented separately in [docs/boards-preview-contract.md](docs/boards-preview-contract.md) and must not be treated as a live-provider-enabled surface.

## Product boundary

Use the backend when Weave needs one of these guarantees:

- validate first-party Weave access tokens and workspace scope
- expose product-specific REST APIs rather than raw downstream protocols
- normalize errors, request ids, and readiness signals for clients and operators
- orchestrate server-owned workflows across identity, chat, files, and calendar
- keep backend-owned service credentials out of apps and logs

Avoid using it to replace standards-based native flows by default:

- no custom server-side login proxy in front of Matrix Native OAuth 2.0
- no assumption that a mobile OIDC bearer token can be reused as a Matrix access token
- no direct Flutter-to-Nextcloud WebDAV/OCS/CalDAV contract as the default live product API

## Repo compass

- `src/main/java/...`: Spring Boot API, auth, product facade, and adapter code.
- `src/main/resources/application.yml`: runtime defaults and environment-variable bindings.
- `src/test/java/...`: contract and service tests for auth, profiles, readiness, files, and calendar behavior.
- `docs/runtime-configuration.md`: complete environment-variable reference and fail-closed adapter behavior.
- `docs/release-operations.md`: Backend API operations guide and minimum operator checks.
- `docs/calendar-client-setup.md`: active research and phased plan for Apple `.mobileconfig`, Android DAVx5, desktop CalDAV, and read-only webcal/ICS setup.
- `docs/context-space-adr.md`: proposed flexible Context/Space contract seam; team/channel stay useful templates, not the hard backend hierarchy.
- `docs/architecture-alignment.md`: cross-repo responsibility split for app, backend, and infrastructure.
- `docs/boards-preview-contract.md`: active feature-gated Boards/Tasks provider-neutral contract notes, now OpenProject-first for read sync with Vikunja/Deck as comparison/fallback; not a Product screenshots or live product surface.
- `src/main/resources/contracts/connector-manifest.schema.json`: internal connector skeleton contract for capabilities, cursors, webhook refs, redaction, and read-only/fail-closed provider seams.
- `src/main/resources/contracts/audit-consent.schema.json` and `docs/audit-consent-seam.md`: internal append-only Audit/Consent seam for future connector/assistant writes; provider writes remain disabled.
- `docs/issues/`: historical alignment issue drafts.

## Quick start

Run tests locally when Java 17 is installed:

```bash
./gradlew test
```

Or run the same suite in Docker:

```bash
docker run --rm \
  -u "$(id -u):$(id -g)" \
  -e HOME=/tmp \
  -e GRADLE_USER_HOME=/tmp/.gradle \
  -v "$PWD:/workspace" \
  -w /workspace \
  eclipse-temurin:17-jdk \
  ./gradlew test
```

Build the local backend image used by `weave-infra` integration runs:

```bash
docker build -t weave-backend:e2e .
```

## Canonical local/dev contract

- Product shell: `https://weave.local`
- Backend API base: `https://api.weave.local/api`
- Keycloak issuer: `https://auth.weave.local/realms/weave`
- Matrix homeserver: `https://matrix.weave.local`
- Weave files/calendar product routes: `https://weave.local/files` and `https://weave.local/calendar`
- Raw Nextcloud technical/admin/protocol fallback: `https://files.weave.local`

Protected `/api/**` routes require a bearer token whose `iss`, `aud`, `azp`/`client_id`, and `scope` match the first-party Weave app contract. Public health, platform config/status, and OpenAPI endpoints are used for bootstrap and diagnostics. Matrix E2EE diagnostics are intentionally conservative: `/api/platform/status` does not claim E2EE completion until every encrypted-room/device/recovery/multi-device/accessibility gate is validated, and it states that encrypted message bodies are opaque to backend diagnostics.

## Operator notes

- Runtime variables and backend-owned actor credentials are documented in [docs/runtime-configuration.md](docs/runtime-configuration.md).
- workspace readiness, stable auth errors, and minimum smoke checks are documented in [docs/release-operations.md](docs/release-operations.md).
- Keep issuer/JWKS/client/audience values aligned with the public auth contract exposed to the app.
- Do not log raw bearer tokens, Nextcloud actor tokens, app passwords, or CalDAV credentials.
