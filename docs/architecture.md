# Weave Architecture

## Overview
Weave uses a feature-first clean architecture with deterministic bootstrap before routing. App-level OIDC bootstrap is resolved before navigation, while protocol-specific or platform-specific code lives either inside the owning feature or in `lib/integrations/<integration>/` when the boundary is shared across multiple features.

Weave is product-first and provider-neutral. It models organization capabilities such as identity, chat, files, calendar, boards/tasks, meetings, decisions, documents/collaboration, embedded manuals, release evidence, and later Weaver. Concrete systems such as Keycloak, Entra ID, Matrix, Teams, Slack, Nextcloud, SharePoint, OpenProject, Jira, or LiveKit attach as provider adapters behind Weave domain contracts. The server is the canonical domain, policy, validation, and control-plane authority. Northbound data planes use open standards where the domain has one: OIDC/OAuth2 for Identity, WebDAV for Files, CalDAV/iCalendar for Calendar, Matrix Client-Server API for Chat through the OIDC-gated Weave Matrix facade, WebRTC plus Weave join grants for Calls, MCP for Agents, and OpenAPI/REST for Admin/control-plane work. The Matrix facade uses a shared Rust/Ruma protocol core through server JNI and Flutter `flutter_rust_bridge`; Synapse or another homeserver can be a southbound provider or fixture, not the northbound product boundary. See [ADR-004: Server OpenAPI is the control-plane contract authority](architecture/adr-004-server-openapi-contract-authority.md). The first Files WebDAV projection decisions are captured in [ADR-005: Files WebDAV facade slice](architecture/adr-005-files-webdav-facade-slice.md). See also [Weave product line and Weaver integration plan](product-line-and-weaver-plan.md), [Organization embedding contract](organization-embedding-contract.md), [Identity provisioning strategy](identity-provisioning-strategy.md), [Provider replacement and anti-silo contract](provider-replacement-and-anti-silo-contract.md), [Canonical domains](architecture/canonical-domains.md), [Provider portability contract](architecture/provider-portability.md), and [Weaver OpenClaw-derived runtime profile](architecture/weaver-openclaw-profile.md).

## Provider-neutral capability contracts

Canonical feature models come before control-plane, Admin Console, infra, or concrete adapter implementation. The active canonical model strategy is documented in [Canonical feature models and provider facades](canonical-feature-models.md), with Mermaid domain diagrams in [`docs/diagrams/`](diagrams/index.md). Server facades expose Weave-owned models per capability; they are not thin provider proxies and must not leak provider IDs, raw provider payloads, secrets, or downstream diagnostics into member or Admin Console product contracts.

Workspace/Admin Health is organized around feature capability categories, not concrete systems. The stable contract categories are identity/IDM, chat, files/documents, calendar, boards/tasks, meetings/calls, decisions/evidence, manuals/help, release evidence, admin control plane, and Weaver runtime. Each category publishes category-level capability keys, current dogfood/default adapters, external adapter placeholders, and operational readiness modules. Member manifests expose only provider-neutral capability states: `available`, `disabled_by_policy`, `not_configured`, `degraded`, `unavailable`, or `coming_later`.

The dogfood stack maps Keycloak, Matrix/Synapse, Nextcloud, OpenProject, ONLYOFFICE, and LiveKit as default adapters only. External adapters such as Entra ID, Microsoft Teams, SharePoint/OneDrive, Microsoft 365 Office/Graph, Planner/Jira, Authentik/Auth0/OIDC/SAML, and other providers attach behind the same category contracts. Normal members never configure raw provider endpoints, secrets, OIDC clients, or diagnostics; admins/operators choose adapters and see support-safe readiness through backend-owned facades.

Provider choices carry an explicit admin-visible posture:

- `recommended_self_hosted_default`: Weave's preferred sovereign/default adapter for the category, still subject to backup, lifecycle, jurisdiction, and operator evidence checks.
- `external_existing_provider`: an organization keeps an existing category provider, for example Teams chat or SharePoint files, while Weave records data residency, retention, audit, export, and support-boundary risks.
- `managed_cloud_provider`: a cloud/SaaS adapter posture that is valid behind the same capability contract but must surface privacy, compliance, availability, export, and vendor-lock-in risks to admins/operators.

This means a mixed deployment such as Keycloak identity, Teams chat, SharePoint/OneDrive files, and OpenProject tasks is architecturally valid. It must not change member-facing state vocabulary or allow direct Flutter-to-provider calls.

Provider replacement is also a backend/admin concern. If an organization swaps a domain provider, for example Slack to Synapse/Matrix for Chat, the server/admin control plane owns preflight, dry-run, mapping, migration, conflict reporting, lossy-field warnings, audit, and readiness. The member client continues to consume canonical Weave Chat models and support-safe impact states.

Design evidence for these seams comes from established interoperability contracts: OpenID Connect describes interoperable authentication on OAuth 2.0; SCIM RFC 7644 standardizes HTTP-based identity management for users/groups; WebDAV RFC 4918 and CalDAV RFC 4791 cover distributed file authoring and calendar access patterns; OASIS CMIS defines a generic content repository model; Microsoft documents Microsoft 365 for the web integration for viewing/editing Office files in the browser. These standards inform adapter boundaries only; each adapter still needs its own risk and license review.

## Organization discovery and manifest contract

The member setup path is intentionally small: a person opens an invite/deep link or enters an organization auth URL, completes SSO, and then the Weave Client fetches `/api/v1/organization/manifest` with the authenticated Weave token. The manifest is the client handoff from organization discovery/auth to member work state.

The manifest may contain the organization display name, the organization auth URL, client-owned responsibilities, Admin Console-owned responsibilities, and effective member capability states. Those states are limited to `available`, `disabled_by_policy`, `not_configured`, `degraded`, `unavailable`, or `coming_later` and are derived from backend capability policy/readiness without exposing provider internals. In plain contract language: available, disabled by policy, not configured, degraded, unavailable, or coming later. The manifest must be support-safe: provider setup, endpoint rotation, diagnostics, and whitelisting are never member-client responsibilities and must not appear as raw provider URLs, secrets, raw downstream errors, or admin-only diagnostics.

The Organization/Admin Console remains the control plane for organization bootstrap, identity-provider configuration, category provider selection, endpoint URL management and rotation, readiness/health, support-safe diagnostics, RBAC/capability profiles, deny-by-default policy, provider/tool/agent whitelisting, risk notes, audit logs, and org-wide defaults. The Weave Client only consumes the resulting manifest/capabilities and renders authenticated member work surfaces.

## App startup
The app now resolves bootstrap before `MaterialApp.router` is built.

Bootstrap phases:

- `loading`
- `needsSetup`
- `needsSignIn`
- `ready`
- `error`

Source of truth:

- a valid persisted `ServerConfiguration` plus an active app OIDC session means `ready`
- a valid persisted `ServerConfiguration` without an app OIDC session means `needsSignIn`
- no persisted configuration means `needsSetup`
- storage/bootstrap failures map to `error`

This avoids the old pattern where the router made an initial decision from a temporary default and then changed after async storage finished loading.

## Routing
We keep:

- `go_router`
- `StatefulShellRoute.indexedStack`
- bottom navigation for authenticated destinations

The router only consumes resolved bootstrap state:

- `needsSetup` can access onboarding routes only
- `needsSignIn` can access the dedicated sign-in route only
- `ready` is redirected away from onboarding to `/chat`
- bootstrap `error` is rendered above routing, not inside redirect logic

Shell destinations:

- Chat
- Files
- Calendar (feature-gated active scope; shared workspace/team/channel calendar hierarchy)
- Tasks/Boards (feature-gated active scope; provider-neutral Weave model, not a Nextcloud Deck product dependency)
- Settings

## Weave Home and chat information architecture

`/chat` is the first Weave Home surface. It is intentionally more structured than a flat Matrix room list:

- **Favorites** collect pinned/favorited people, channels, and AI chats once that metadata is available.
- **Personal messages** contain direct human-to-human conversations.
- **Channels** contain team/topic rooms and remain the main collaboration spine for future channel workspaces.
- **AI chats** provide a distinct home for specialized assistant and agent chats instead of mixing them into ordinary DMs.

The member Chat data plane is the Matrix Client-Server API projection over Weave Chat; Slack and Teams stay southbound bridge/provider adapters. Flutter wires `ChatRepository` to the Matrix projection for normal sync/send. `/api/chat/*` remains only for readiness, admin/operator migration evidence, decisions, meeting capsules, and Weaver scout product-control surfaces. REST conversation/message compatibility routes are obsolete and removed; member sync/send must enter through `/_matrix/client/**`. Channel detail treats a channel as a workspace container, but normal member copy may only show available product surfaces or impact-level unavailable states. Files, board/task, calendar, and meeting setup details stay behind admin/operator Workspace Health until the corresponding backend capability is enabled; channel UX must not expose provider setup diagnostics or preview claims.

The server owns a Chat domain facade seam and an OIDC-gated Matrix Client-Server projection. `/_matrix/client/**` is served on the public API origin as a Weave northbound facade, not a Synapse container or raw provider passthrough; a `matrix.<tenant>` origin remains a southbound provider/operator detail. The facade projects canonical Chat conversations/messages and delegates protocol-shape work to the Rust/Ruma core boundary. Calendar event REST CRUD is obsolete as a member data plane; Calendar sync and event CRUD move through `/caldav/**`, while `/api/calendar/**` remains setup/readiness/access-policy control plane. Admin/operator routes under `/api/admin/chat/*` may show support-safe selected mapping, redacted readiness diagnostics, and migration dry-run/preflight reports; destructive migration apply is intentionally out of scope.

The remaining canonical server domain facades are represented by non-Chat skeleton contracts for Files/Documents, Calendar/Meetings, Boards/Tasks, and Identity/Admin/Policy. They are Weave product contracts, not provider proxies: each names canonical object kinds and adapter-boundary operations, evaluates capability policy before provider lookup, fails closed for unknown capabilities, returns empty Weave-domain collections until a promoted adapter exists, and exposes only support-safe admin mappings with SecretRef presence flags rather than secret material, raw provider URLs, downstream payloads, or provider errors.

## Organization bootstrap configuration

`GET /api/platform/config` returns the strict provider-neutral `OrgManifest` v1 pinned by the canonical specification corpus. The member client resolves it from the organization origin and consumes only the OIDC contract plus Weave-owned Matrix Client-Server, WebDAV Files, and CalDAV Calendar facade bases. The manifest includes provider-neutral state for Identity, Chat, Files, Calendar, Boards, and Health; it contains no provider identifier, provider URL, provider reality level, or editable provider override.

`features/server_config/` persists the resolved OIDC and Weave facade configuration needed for session restore. When older preference data is loaded, stale Matrix or Files-provider endpoint values are discarded and reconstructed from the Weave control-plane base. Manual setup exposes the organization/identity and Weave API boundary only; provider endpoints are not member-editable settings.

## Persistence split
Persistence is split by responsibility:

- `PreferencesStore` for harmless configuration and future non-sensitive flags
- `SecureStore` for tokens, sensitive protocol credentials, and persisted sessions that should not live in preferences

Current secure-storage usage is app-auth session persistence. Dogfood reset still deletes the old `nextcloud_session_v1` key so upgraded developer devices clear stale provider-client state without keeping the removed integration package.

## Failure model
`AppFailure` is the shared app-level failure model used across bootstrap, storage, and repositories. Presentation should respond to `AppFailure` rather than raw package exceptions.

Current failure types:

- `bootstrap`
- `storage`
- `validation`
- `unknown`

## External collaboration and interop

Slack, Teams, guest collaboration, migration tooling, and connectors must attach through backend-owned Interop Gateway boundaries rather than provider-specific Flutter transport logic. See [Interop Gateway and External Collaboration](interop-gateway-and-external-collaboration.md). Interop remains feature-flagged/off by default until explicitly promoted.

## Active tasks and boards scope

Tasks/boards are active Weave scope behind feature gates. Build a Weave-owned, accessibility-first board/task model with provider adapters rather than exposing Nextcloud Deck or any other upstream tool as the product model. See [Product scope: calendar hierarchy, Matrix E2EE, and Boards](product-calendar-e2ee-boards-scope.md), [Boards and Tasks Provider Strategy](research/boards-task-module-provider-strategy.md), and [Boards and Tasks Domain Contract](research/boards-task-domain-contract.md).

## Feature and integration layering
Each feature follows the same three layers:

- `presentation/`
- `domain/`
- `data/`

Shared integrations follow the same layering under `lib/integrations/<integration>/` when multiple features need the same protocol/platform boundary.

Current feature repository boundaries:

- `auth` -> `AuthSessionRepository` + `OidcClient`
- `chat` -> `ChatRepository` + Matrix Client-Server projection mapping in `data/`; backend Chat facade OpenAPI DTO mapping remains a fenced control-plane/conformance seam and Slack/Teams never become northbound member data-plane transports
- `files` -> `FilesRepository` + Weave WebDAV data-plane mapping in `data/`, with OpenAPI retained for discovery/readiness/setup/revoke/control-plane state; direct provider WebDAV/Nextcloud transport is removed from Flutter member code
- `calendar` -> `CalendarRepository` + Weave CalDAV/iCalendar projection for the event data plane, with backend OpenAPI retained for discovery/readiness/setup/revoke/control-plane state; direct provider CalDAV/Graph/Google transport stays behind server adapters
- `deck` / future `tasks_boards` -> exploratory board repository/client boundaries; future work should use a provider-neutral Weave model with adapters

Presentation depends on repository contracts and Riverpod providers only. It does not own storage or protocol logic.

Boundary rule:

- feature-specific mapping stays in the feature
- generated OpenAPI DTOs are transport contracts for feature `data/` mappers, not presentation or domain models
- shared OpenAPI adapter primitives may represent reusable resource pages, readiness/capability states, support-safe errors, and future watch-stream envelopes, while Chat, Files, and other features keep feature-specific repository methods where their semantics differ
- reusable external-service auth/session/orchestration belongs in an integration layer
- features may depend on integrations, but integrations must not depend on feature presentation state or feature-owned transport mappings they are meant to support

## Session separation
App auth, the Weave Matrix facade session boundary, and Files facade state are intentionally separate concerns:

- `auth/` owns the app-level OIDC session that decides whether the shell is reachable
- `chat/data/repositories/WeaveMatrixFacadeChatRepository` consumes the OIDC-gated Weave Matrix Client-Server projection for member chat sync/send; the obsolete REST `BackendChatRepository` has been removed
- `files/data/repositories/BackendFilesRepository` consumes the Weave app session and calls the canonical backend Files facade; the old Flutter `integrations/nextcloud/` provider client is removed
- the app does not call a raw Matrix homeserver or persist a Matrix SDK access token; the Weave Matrix facade validates the app-level OIDC token
- changing the Matrix facade URL invalidates chat data-plane requests without redesigning bootstrap
- changing configured Files/backend endpoints invalidates Files view state without feature-owned provider-session cleanup

Matrix E2EE state also stays inside `features/chat/`:

- the previous Dart Matrix SDK crypto path is retired and must not be reintroduced
- bootstrap, trust, verification, and recovery state remain chat-owned models before UI consumes them
- settings may host chat-owned security UI, but other features must not depend on raw Matrix crypto objects
- recovery keys must be treated as external user-held material; local secure storage can help cache secrets, but reinstall/device-restore behavior differs across Android, iOS, and macOS and must not be overclaimed
- the current Flutter security repository fails closed until generated `flutter_rust_bridge` bindings expose Rust Matrix core device verification and recovery behavior
- future verification states must stay chat-owned and surface recovery/unlock prompts rather than raw protocol or SDK state names

The Matrix integration is the standard Chat data-plane seam. It uses:

- the configured Weave Matrix facade URL from `ServerConfiguration`
- OIDC bearer tokens from the app session, validated by Spring Boot before Matrix responses are emitted
- `/_matrix/client/versions`, `/sync`, `/joined_rooms`, `/rooms/{roomId}/messages`, and `/rooms/{roomId}/send/m.room.message/{txnId}` for the current member data-plane slice
- the shared Rust/Ruma Matrix core boundary through server JNI and Flutter `flutter_rust_bridge`

## Legacy Nextcloud Flutter Integration
Normal member Files uses the Weave WebDAV facade through `BackendFilesRepository` for list, read, upload, create-folder, and delete data-plane behavior; Flutter keeps OpenAPI only for discovery/readiness/setup/revoke/control-plane state. Server-side `/dav/files` supports guarded `PUT`, `MKCOL`, and `DELETE` with ETags, conditional preconditions, support-safe errors, and mutation audit, and the Flutter repository calls those WebDAV methods instead of legacy OpenAPI member data-plane endpoints.

The old Flutter `integrations/nextcloud/` auth/session/Login Flow package has been removed. Nextcloud remains a valid southbound server adapter, but member-client code must not own raw Nextcloud sessions, app passwords, provider DAV validation, or provider Login Flow. Future Calendar or provider-adapter board work must not import `features/files/` or add direct member UI provider setup paths.

## Calendar backend facade scope

Calendar is active shared-scheduling scope and moves through the Weave CalDAV/iCalendar projection rather than direct provider CalDAV. The product model is shared scheduling: workspace calendar, team calendars, and channel calendars/events/meeting threads. Backend OpenAPI remains for calendar setup, readiness, revoke, scoped credentials, generated models, and admin/operator control. CalDAV collections expose all three shared scopes. Each projected event carries canonical context and stable meeting-thread metadata; concrete Matrix room/thread bindings remain optional.

Private personal calendars are out of scope for the current product path. Frontend code must continue to fail through the backend facade and must not add a direct private-personal CalDAV fallback or secret-bearing client setup path.

## Onboarding and settings
Onboarding setup and Settings share:

- the same `ServerConfiguration` domain model
- the same repository
- the same derivation rules
- the same form controller logic

The UI differs by role and context:

- admin/operator onboarding presents the config as a guided setup flow before inviting users
- settings presents the same data as an editable configuration section only to owners/admins
- members and guests see sign-in, profile, personal settings, and impact/fallback workspace status; they do not configure OIDC, realm, organization, or provider endpoint details
- Workspace Health is the admin/operator control plane for setup, readiness, degraded provider state, and support-safe next actions

Acceptance boundary for issues #259, #250, and #212:

- owner/admin roles may see organization setup, Workspace Health, provider readiness, and support-safe diagnostics
- member/guest roles must not see OIDC setup forms, service endpoint forms, provider stack diagnostics, raw provider failures, or preview/coming-soon release-scope copy in normal home/settings/channel paths
- hidden or unavailable capabilities must be explained to members as product impact and safe fallback, with admins directed to Workspace Health for remediation

## Accessibility expectations
Refactors in this area must preserve:

- minimum `48x48` touch targets
- semantics labels for icon-only affordances
- readable focus order in setup/settings
- clear announcements for bootstrap loading and retry states

## IDM/RBAC capability profile contract

Weave derives workspace capability visibility from the selected IDM and backend-owned policy evaluation. Keycloak is the self-hosted default IDM for dogfood deployments, but the contract is adapter-friendly for OIDC/SAML providers such as Entra ID, Authentik, and Auth0.

The backend consumes realm roles and groups, normalizes them into capability profiles, and grants only category-level capability keys. Examples include chat.read, chat.send, files.read, files.upload, calendar.read, calendar.manage_events, boards.read, boards.update_task, weaver.files_read, and weaver.exec_disabled. Unknown roles/groups are deny-by-default.

Capability policy responses are support-safe: they expose effective policy posture and profile keys, not provider secrets or raw setup. Weaver runtime enablement is intentionally absent from built-in profiles until a later governed runtime policy can generate per-user, audited, sandboxed runtime configuration.

## Governed Weaver runtime profile contract

Weaver runtime integration consumes the workspace capability policy produced by IDM/RBAC. The runtime endpoint returns a support-safe `workspace-capability-policy` generated profile; it does not expose raw provider setup, secrets, OpenClaw internals, or a second agent-specific policy model.

The generated profile is per-user and Docker-oriented: it names the baseline profile/image, isolated workspace root, isolated agent directory, Docker network mode, plugin allowlist, tool allowlist, and the capability keys visible to the runtime. Disabled_by_policy and unavailable users receive the same contract shape with `enabled=false` and impact-level posture.

Runtime provisioning remains fail-closed unless all three gates pass: the Weaver workspace category is enabled, the governed runtime generator is enabled, and the user's Weave capability profile grants `weaver.enabled`. Allowed runtime capabilities are the intersection of Weave policy grants and the admin runtime allowlist. exec and elevated surfaces stay disabled by default and require future constrained admin policy before they can appear.

Profile generation publishes a support-safe audit event so later runtime start and tool-use flows can prove who generated a profile, which policy produced it, and whether exec/elevated surfaces were absent.
