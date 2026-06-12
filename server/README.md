# Weave Backend

[![CI](https://github.com/masssi164/weave-backend/actions/workflows/ci.yml/badge.svg)](https://github.com/masssi164/weave-backend/actions/workflows/ci.yml)

**Product API/BFF for safe Weave collaboration surfaces.**

`weave-backend` is the Spring Boot product boundary between Weave clients and the self-hosted provider stack. It validates Weave access tokens, exposes stable product APIs, normalizes readiness/errors, keeps backend-owned credentials server-side, and refuses unsafe provider paths by default.

It is intentionally not a generic proxy for Matrix, Nextcloud, Keycloak, OpenProject, GitLab, Forgejo, ONLYOFFICE, Collabora, or future connectors. Flutter may use native OIDC and Matrix flows where those are the correct client protocols; everything that needs product orchestration, provider secrets, support-safe diagnostics, or fail-closed behavior belongs here.

## What the backend owns

- JWT issuer, audience, client, and `weave:workspace` scope validation.
- Product APIs for profile, onboarding, workspace capabilities, readiness, files, calendar, DevOps readiness, Matrix/MAS policy status, provider-stack status, and later documents/collaboration launch seams once the domain-facade foundation is ready.
- Backend-held actors and provider credentials for server-side facades.
- Support-safe error envelopes, request IDs, redaction, and diagnostics.
- Feature gates for unsafe or incomplete provider paths.
- Internal audit/consent seams for future connector or assistant writes.
- OpenAPI and deterministic test contracts.

## What the backend does not own

- Raw provider UI embedding as a normal Weave product surface.
- Generic credential brokering or bearer-token forwarding to clients.
- A custom login proxy in front of standards-based OIDC/Matrix flows.
- Direct Flutter-to-provider contracts for Nextcloud WebDAV/OCS/CalDAV, OpenProject, GitLab, Forgejo, ONLYOFFICE, Collabora, Slack, Teams, or other provider runtimes.
- Provider writes until authorization, audit, consent, smoke/E2E, export/recovery, and accessibility gates are promoted.

## Active API scope

Currently implemented or contract-backed surfaces:

- Public health/platform bootstrap endpoints for gateways and smoke checks.
- `GET /api/me` caller snapshot.
- `GET /api/profile`, `PATCH /api/profile`, and `GET /api/profile/sync-status`.
- `GET /api/onboarding/status`.
- `GET /api/workspace/capabilities` and `GET /api/workspace/release-readiness`.
- Chat domain facade at `/api/chat/**`, `/api/v1/chat/**`, and `/api/admin/chat/**`: member APIs expose canonical Weave conversations/messages/readiness, membership, history-policy, attachment-policy, fail-closed state, and audited sends gated by `chat.read`, `chat.send`, and Context/Space authorization; admin APIs expose selected Chat mapping, support-safe readiness, and audited migration dry-run/preflight reports.
- Admin/operator Chat provider replacement dry-run at `/api/admin/chat/provider-replacements/dry-run` with lossy-mapping warnings, conflict evidence, and redacted provider diagnostics.
- Canonical domain registry v1 in `/api/providers/status` from `src/main/resources/canonical-domain-registry-v1.json`, copied deterministically from `specs/0004-domain-registry/canonical-domain-registry-v1.json` and guarded by `./gradlew domainRegistryCheck`, covering identity, people, spaces, chat, files, documents, calendar, boards, calls, decisions, notifications, health, and Weaver with member/admin states, compatibility aliases, portability metadata, and no-unaccounted-data-loss migration primitives.
- Canonical non-Chat domain facade contracts for Files/Documents, Calendar/Meetings, Boards/Tasks, and Identity/Admin/Policy. These server-side seams evaluate Weave capability policy before provider lookup, fail closed for unknown capabilities, expose SecretRef-only admin mappings, and return empty Weave-domain skeleton collections until concrete adapters are promoted.
- Files facade backed by Nextcloud when a backend actor is configured; otherwise fail-closed.
- Calendar facade for workspace/team/channel collections; unsafe private-personal calendar templates fail closed.
- Secret-free calendar client setup metadata at `GET /api/calendar/client-setup`.
- Provider stack readiness at `GET /api/providers/status`, including Nextcloud WebDAV/CalDAV/CardDAV/Forms, Keycloak OIDC, Synapse/Matrix, MAS, fail-closed meeting support, and OpenProject readiness seams.
- DevOps readiness through backend facades; disabled/unconfigured providers expose support-safe, fail-closed status without product data leakage.
- Documents/collaboration and Office-style launch seams remain postponed behind backend facades; any existing experimental launch errors stay support-safe and fail closed.
- Boards/Tasks workspace facade and OpenProject workspace-sync validation contracts behind explicit runtime, authorization, and audit gates.
- OpenAPI JSON at `/v3/api-docs`.

## Provider and readiness posture

The provider stack is backend-owned by design:

- Missing credentials produce unavailable/degraded readiness instead of insecure fallback behavior.
- Optional providers default off or not configured.
- Diagnostics must not expose raw provider URLs, response bodies, bearer tokens, API tokens, cookies, app passwords, or signing secrets.
- Chat and canonical non-Chat domain responses use stable product states (`ready`, `disabled`, `degraded`, `policy_blocked`, `unavailable`, `misconfigured`, `unsupported`) and never ask members to configure raw providers, endpoints, credentials, downstream payloads, or migration diagnostics.
- DevOps provider modules expose no linked projects, repositories, issues, merge requests, pipelines, or releases while disabled.
- Documents/collaboration launch paths refuse unsafe states with stable error codes instead of leaking downstream details, and are lower priority than the shared domain-facade/provider-swap foundation.
- Matrix/MAS status stays support-safe: Matrix client protocol remains the direct-client exception, encrypted message bodies are not server-readable, and video-call/meeting support is deferred/fail-closed.
- Boards user writes stay backend-facade-owned, explicit, authorized, and auditable; OpenProject provider writes stay disabled until promotion gates pass.

## Runtime and operations docs

- [Runtime configuration](docs/runtime-configuration.md): environment variables, adapter gates, fail-closed behavior.
- [Release operations](docs/release-operations.md): smoke checks, readiness, OpenAPI, and operator notes.
- [Architecture alignment](docs/architecture-alignment.md): cross-repo responsibility split.
- [Calendar client setup](docs/calendar-client-setup.md): secret-free setup metadata and blocked profile/credential flows.
- [Context/Space ADR](docs/context-space-adr.md): flexible collaboration context model and authorization seam.
- [Boards workspace contract](docs/boards-workspace-contract.md): provider-neutral Boards/Tasks workspace contract; OpenProject is optional provider-backed workspace-sync behind the Weave backend facade.
- [Audit/Consent seam](docs/audit-consent-seam.md): internal safety layer for future writes.
- `src/main/resources/contracts/`: contract artifacts for boards workspace, connector manifests, context/space, and audit/consent.

Historical issue drafts live under `docs/issues/`; do not treat them as current public product docs without checking the active contracts above.

## Local development

Run tests with Java 21+:

```bash
./gradlew test
```

Or run them in Docker:

```bash
docker run --rm \
  -u "$(id -u):$(id -g)" \
  -e HOME=/tmp \
  -e GRADLE_USER_HOME=/tmp/.gradle \
  -v "$PWD:/workspace" \
  -w /workspace \
  eclipse-temurin:21-jdk \
  ./gradlew test
```

Build the local image used by monorepo `infra/` live-stack runs:

```bash
docker build -t weave-backend:e2e .
```

## Canonical local/dev contract

- Product shell: `https://weave.test`
- Backend API base: `https://api.weave.test/api`
- Keycloak issuer: `https://auth.weave.test/realms/weave`
- Matrix homeserver: `https://matrix.weave.test`
- Weave files/calendar product routes: `https://weave.test/files` and `https://weave.test/calendar`
- Raw Nextcloud technical/admin/protocol fallback: `https://files.weave.test`

Protected `/api/**` routes require a bearer token whose issuer, audience, authorized party/client id, and scope match the first-party Weave app contract. Public health, platform config/status, and OpenAPI endpoints support bootstrap and diagnostics.

Matrix E2EE diagnostics are conservative by design: `/api/platform/status` does not claim E2EE completion until encrypted-room, device, recovery, multi-device, metadata-boundary, and accessibility gates are validated.

## Security rules

- Do not log bearer tokens, provider API tokens, app passwords, cookies, signing secrets, raw provider errors, or raw provider URLs.
- Keep backend actors and provider credentials out of Flutter, generated app config, support bundles, and user-visible diagnostics.
- Prefer stable Weave error codes and support-safe summaries over downstream exception text.
- Fail closed when provider state is unknown, disabled, not configured, or unsafe.
- Treat provider writes as unavailable until an explicit promotion contract says otherwise.
