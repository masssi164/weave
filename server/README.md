# Weave Backend

[![CI](https://github.com/masssi164/weave/actions/workflows/ci.yml/badge.svg)](https://github.com/masssi164/weave/actions/workflows/ci.yml)

**Product API/BFF for safe Weave collaboration surfaces.**

The `server/` module is the Spring Boot product boundary between Weave clients and configured providers. It validates Weave access tokens, exposes product APIs, normalizes readiness/errors, keeps backend-owned credentials server-side, and refuses unsafe provider paths by default. Weave does not depend on the owner's private Home-core installation.

It is intentionally not a generic proxy for Matrix, Nextcloud, Keycloak, OpenProject, GitLab, ONLYOFFICE, Collabora, or future connectors. Flutter may use native OIDC and Matrix flows where those are the correct client protocols; everything that needs product orchestration, provider secrets, support-safe diagnostics, or fail-closed behavior belongs here.

## What the backend owns

- JWT issuer, audience, client, and `weave:workspace` scope validation.
- Product APIs for profile, organization handoff, workspace capabilities, readiness, files, calendar, DevOps readiness, Matrix/MAS policy status, provider-stack status, and later documents/collaboration launch seams once the domain-facade foundation is ready.
- Backend-held actors and provider credentials for server-side facades.
- Support-safe error envelopes, request IDs, redaction, and diagnostics.
- Feature gates for unsafe or incomplete provider paths.
- Internal audit/consent seams for future connector or assistant writes.
- OpenAPI and deterministic test contracts.

## What the backend does not own

- Raw provider UI embedding as a normal Weave product surface.
- Generic credential brokering or bearer-token forwarding to clients.
- A custom login proxy in front of standards-based OIDC/Matrix flows.
- Direct Flutter-to-provider contracts for Nextcloud WebDAV/OCS/CalDAV, OpenProject, GitLab, ONLYOFFICE, Collabora, Slack, Teams, or other provider runtimes.
- Provider writes without current authorization, audit, preconditions, and recovery evidence.

## API implementation and release boundary

The approved release is tracked in [#1470](https://github.com/masssi164/weave/issues/1470). The inventory below includes older compatibility code; its presence does not establish current release acceptance. Public northbound DAV, Calls and private runtime execution are outside this release. Provider adoption and migration are tracked separately in [#1498](https://github.com/masssi164/weave/issues/1498). Matrix compatibility is bounded by the [normative support profile](../docs/reference/matrix-client-server-support-profile.md); independent-client interoperability, automatic audience-bound session establishment and client-owned E2EE still require their acceptance evidence.

Current implementation inventory:

- Public health/platform bootstrap endpoints for gateways and smoke checks.
- `GET /api/me` caller snapshot.
- `GET /api/profile`, `PATCH /api/profile`, and `GET /api/profile/sync-status`.
- `GET /api/workspace/capabilities` and `GET /api/workspace/release-readiness`.
- OIDC-gated Matrix Client-Server facade at `/_matrix/client/**`: member room sync, encrypted timeline/send, receipts, typing, reactions, redaction, room lifecycle, device keys, cross-signing, to-device traffic, room-key backup, recovery, and identity operations project the canonical Chat domain through the shared Rust/Ruma JNI core. Spring authorization and `chat.read`/`chat.send` capability checks run before the replaceable southbound `ChatProviderPort`; writes are audited without exposing provider payloads. This is a Weave protocol facade, not a northbound Synapse or other homeserver.
- Matrix private keys, Olm/Megolm sessions, decrypted bodies, verification state machines, and recovery secrets belong to the Flutter-side Apache-2.0 Matrix Rust SDK. The backend persists public keys and opaque protocol envelopes only and rejects plaintext writes to encrypted rooms.
- Chat control and context APIs at `/api/chat/readiness`, `/api/v1/chat/**`, `/api/admin/chat/**`, and `/api/v1/admin/chat/**`: member routes retain support-safe readiness, decisions, and meeting capsules; admin routes retain selected Chat mapping and audited migration dry-run/preflight reports. Deprecated REST conversation/message data-plane and Weaver Scout routes are unavailable.
- Admin/operator Chat provider replacement dry-run at `/api/admin/chat/provider-replacements/dry-run` with lossy-mapping warnings, conflict evidence, and redacted provider diagnostics.
- Canonical domain registry v1 in `/api/providers/status` from `src/main/resources/canonical-domain-registry-v1.json`, copied deterministically from `specs/0004-domain-registry/canonical-domain-registry-v1.json` and guarded by `./gradlew domainRegistryCheck`, covering identity, people, spaces, chat, files, documents, calendar, boards, calls, decisions, notifications, health, and Weaver with member/admin states, compatibility aliases, portability metadata, and no-unaccounted-data-loss migration primitives.
- Canonical non-Chat domain facade contracts for Files/Documents, Calendar/Meetings, Boards/Tasks, and Identity/Admin/Policy. These server-side seams evaluate Weave capability policy before provider lookup, fail closed for unknown capabilities, expose SecretRef-only admin mappings, and return empty Weave-domain skeleton collections until concrete adapters are promoted.
- Generated User Files routes under `/api/files/items` list and inspect explicitly Weave-attached items and dispatch supported operations through the active Files binding. The native adapter supports root-folder creation, absent-name upload and identity-bound bounded downloads. Responses keep an opaque Weave `FileId`, a display-only logical path and current `allowedActions`. Creation requires an explicit idempotency key and `If-None-Match: *`; downloads return a strong content ETag and `Content-Digest`. Binary transfers are limited to 25 MiB. The generated content-update operation requires atomic object-identity and version checks, which the current adapters do not advertise.
- Files exposes only resources with a known Weave Space and owner grant. Service-account-visible, unmapped provider objects remain hidden. Nextcloud root creation checks the returned `OC-FileId` against `oc:id` readback before publishing a mapping. Its path-bound conditional DAV GET cannot atomically guarantee object identity, so the User API does **not** advertise or serve Nextcloud downloads. Creation beneath a mapped folder also fails closed before intent/provider access; those folders do not advertise upload or create actions. Root creation assumes the binding's validated provider root remains the same namespace during the request; an out-of-band remount is not atomically fenced.
- Files sharing, rename/move, copy, delete and general permission inspection remain unfinished in this User slice. A binding with existing mappings currently blocks credential rotation and replacement until verified identity carry-forward exists. These limitations are not evidence of completed #1472 or #1498 acceptance.
- CalDAV/iCalendar facade for workspace/team/channel collections, including stable canonical context and meeting-thread metadata on northbound `VEVENT` projections; unsafe private-personal calendar templates fail closed.
- Secret-free calendar client setup metadata at `GET /api/calendar/client-setup`.
- Provider stack readiness at `GET /api/providers/status`, including Nextcloud WebDAV/CalDAV/CardDAV/Forms, Keycloak OIDC, Synapse/Matrix, MAS, fail-closed meeting support, and OpenProject readiness seams.
- DevOps readiness through backend facades; disabled/unconfigured providers expose support-safe, fail-closed status without product data leakage.
- Documents/collaboration and Office-style launch seams remain postponed behind backend facades; any existing experimental launch errors stay support-safe and fail closed.
- Boards/Tasks workspace facade and OpenProject workspace-sync validation contracts behind explicit runtime, authorization, and audit gates.
- Separate generated User/Admin OpenAPI JSON at `/v3/api-docs/user` and `/v3/api-docs/admin` (plus the combined diagnostic document at `/v3/api-docs`).

### Code-first OpenAPI

Controller annotations, transport DTOs and validation own the HTTP artifacts. From the repository root, run `./gradlew generateOpenApiContract` after a contract change and `./gradlew checkOpenApiContractFresh` to verify the committed projections. Do not edit `contracts/openapi/*.json` by hand. Every currently exported User/Admin operation has an explicit ID; export tests compare it with the actual Spring handler, check uniqueness and require equality across grouped and combined documents. Normalization preserves ordered examples, schema meaning, security metadata, response headers and error responses.

JVM consumers use `weave-user-api-client` and `weave-admin-api-client`; their `check` tasks regenerate and compare the pinned client output. The User client includes a guarded generator correction for binary request bodies, verified against actual HTTP bytes. Generated transport consistency does not replace independent authorization and behavioral assertions. The product E2E uses that same User client to verify Files identity, exact bytes, validators, retry conflicts, outsider denial and persistence after service restart.

The current export is an implementation inventory, not a claim that the complete product operation set is delivered. Calendar event REST operations, complete Files operations and classification of older privileged readiness/setup routes remain tracked by #1472/#1479.

## Provider and readiness posture

The provider stack is backend-owned by design:

- Missing credentials produce unavailable/degraded readiness instead of insecure fallback behavior.
- Optional providers default off or not configured.
- Diagnostics must not expose raw provider URLs, response bodies, bearer tokens, API tokens, cookies, app passwords, or signing secrets.
- Chat and canonical non-Chat domain responses use stable product states (`ready`, `disabled`, `degraded`, `policy_blocked`, `unavailable`, `misconfigured`, `unsupported`) and never ask members to configure raw providers, endpoints, credentials, downstream payloads, or migration diagnostics.
- DevOps provider modules expose no linked projects, repositories, issues, merge requests, pipelines, or releases while disabled.
- Documents/collaboration launch paths refuse unsafe states with stable error codes instead of leaking downstream details, and are lower priority than the shared domain-facade/provider-swap foundation.
- Matrix/MAS status stays support-safe: Flutter calls the Weave-owned Matrix facade, never a raw southbound homeserver; encrypted message bodies are not server-readable, and video-call/meeting support is deferred/fail-closed.
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

When Docker is available, the optional S3 adapter integration test uses the verified
source-built MinIO fixture. Prepare its local tag before running `:server:test`:

```bash
s3_fixture_id="$(infra/weave-workspace/scripts/build_runtime_state_image.sh)"
docker tag "$s3_fixture_id" weave-runtime-state:ci-s3
./gradlew :server:test
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
- Current dev southbound Matrix provider: `https://matrix.weave.test` (technical legacy route, not the #1475 product contract). OrgManifest/gateway conformance must advertise and route to the Weave Matrix Client-Server facade before claiming the current release's Chat journey.
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
