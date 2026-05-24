# Weave Architecture

## Overview
Weave uses a feature-first clean architecture with deterministic bootstrap before routing. App-level OIDC bootstrap is resolved before navigation, while protocol-specific or platform-specific code lives either inside the owning feature or in `lib/integrations/<integration>/` when the boundary is shared across multiple features.

Weave is product-first and provider-neutral. It models organization capabilities such as identity, chat, files, calendar, boards/tasks, meetings, documents/collaboration, and later Weaver. Concrete systems such as Keycloak, Entra ID, Matrix, Teams, Nextcloud, SharePoint, OpenProject, Jira, or LiveKit attach as provider adapters behind Weave contracts. See [Weave product line and Weaver integration plan](product-line-and-weaver-plan.md).

## Provider-neutral capability contracts

Canonical feature models come before control-plane, Admin Console, infra, or concrete adapter implementation. The active canonical model strategy is documented in [Canonical feature models and provider facades](canonical-feature-models.md), with Mermaid domain diagrams in [`docs/diagrams/`](diagrams/). Server facades expose Weave-owned models per capability; they are not thin provider proxies and must not leak provider IDs, raw provider payloads, secrets, or downstream diagnostics into member or Admin Console product contracts.

Workspace/Admin Health is organized around feature capability categories, not concrete systems. The stable contract categories are identity/IDM, chat, files, office/docs collaboration, meetings/calls, boards/tasks, calendar, and Weaver runtime. Each category publishes category-level capability keys, current dogfood/default adapters, external adapter placeholders, operational readiness modules, and the stable member impact states `usable`, `disabled`, `degraded`, and `policy-blocked`.

The dogfood stack maps Keycloak, Matrix/Synapse, Nextcloud, OpenProject, ONLYOFFICE, and LiveKit as default adapters only. External adapters such as Entra ID, Microsoft Teams, SharePoint/OneDrive, Microsoft 365 Office/Graph, Planner/Jira, Authentik/Auth0/OIDC/SAML, and other providers attach behind the same category contracts. Normal members never configure raw provider endpoints, secrets, OIDC clients, or diagnostics; admins/operators choose adapters and see support-safe readiness through backend-owned facades.

Provider choices carry an explicit admin-visible posture:

- `recommended_self_hosted_default`: Weave's preferred sovereign/default adapter for the category, still subject to backup, lifecycle, jurisdiction, and operator evidence checks.
- `external_existing_provider`: an organization keeps an existing category provider, for example Teams chat or SharePoint files, while Weave records data residency, retention, audit, export, and support-boundary risks.
- `managed_cloud_provider`: a cloud/SaaS adapter posture that is valid behind the same capability contract but must surface privacy, compliance, availability, export, and vendor-lock-in risks to admins/operators.

This means a mixed deployment such as Keycloak identity, Teams chat, SharePoint/OneDrive files, and OpenProject tasks is architecturally valid. It must not change member-facing state vocabulary or allow direct Flutter-to-provider calls.

Design evidence for these seams comes from established interoperability contracts: OpenID Connect describes interoperable authentication on OAuth 2.0; SCIM RFC 7644 standardizes HTTP-based identity management for users/groups; WebDAV RFC 4918 and CalDAV RFC 4791 cover distributed file authoring and calendar access patterns; OASIS CMIS defines a generic content repository model; Microsoft documents Microsoft 365 for the web integration for viewing/editing Office files in the browser. These standards inform adapter boundaries only; each adapter still needs its own risk and license review.

## Organization discovery and manifest contract

The member setup path is intentionally small: a person opens an invite/deep link or enters an organization auth URL, completes SSO, and then the Weave Client fetches `/api/v1/organization/manifest` with the authenticated Weave token. The manifest is the client handoff from organization discovery/auth to member work state.

The manifest may contain the organization display name, the organization auth URL, client-owned responsibilities, Admin Console-owned responsibilities, and effective member capability states. Those states are limited to `ready`, `disabled`, `degraded`, or `policy-blocked` and are derived from backend capability policy/readiness. In plain contract language: ready, disabled, degraded, or policy-blocked. The manifest must be support-safe: provider setup, endpoint rotation, diagnostics, and whitelisting are never member-client responsibilities and must not appear as raw provider URLs, secrets, raw downstream errors, or admin-only diagnostics.

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

The first implementation slice keeps Matrix as the conversation source, classifies direct messages versus channels from existing room metadata, and renders honest empty states for favorites and AI chats until backend/product metadata is ready. Channel detail treats a channel as a workspace container, but normal member copy may only show ready product surfaces or impact-level unavailable states. Files, board/task, calendar, and meeting setup details stay behind admin/operator Workspace Health until the corresponding backend capability is enabled; channel UX must not expose provider setup diagnostics or preview claims.

## Shared server configuration
`features/server_config/` owns the shared configuration model used by both onboarding and settings.

Persisted fields:

- `providerType`
- `oidcIssuerUrl`
- `matrixHomeserverUrl`
- `nextcloudBaseUrl`
- `backendApiBaseUrl`

Derivation rule:

- parse the issuer URL
- use the issuer host
- if the host has 3 or more labels, drop the first label
- derive:
  - `https://matrix.<base-domain>`
  - `https://files.<base-domain>`
  - `https://api.<base-domain>/api`

Example:

- `https://auth.home.internal` becomes `https://matrix.home.internal`, `https://files.home.internal`, and `https://api.home.internal/api`

This is intentionally simple, explicit, and easy to change later. It is a convenience default, not a hard rule. Users can edit the derived values during setup and in Settings.

## Persistence split
Persistence is split by responsibility:

- `PreferencesStore` for harmless configuration and future non-sensitive flags
- `SecureStore` for tokens, sensitive protocol credentials, and persisted sessions that should not live in preferences

Current secure-storage usage includes app-auth session persistence and the shared Nextcloud session store.

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

Current repository-first stub boundaries:

- `auth` -> `AuthSessionRepository` + `OidcClient`
- `chat` -> `ChatRepository` + `MatrixClient`
- `integrations/nextcloud` -> `NextcloudConnectionService` + `NextcloudAuthClient` + `NextcloudSessionRepository` + shared providers
- `files` -> `FilesRepository` + `NextcloudDavClient`
- `calendar` -> `CalendarRepository` + backend `CalendarFacadeClient` (no direct Flutter-to-CalDAV product path)
- `deck` / future `tasks_boards` -> exploratory board repository/client boundaries; future work should use a provider-neutral Weave model with adapters

Presentation depends on repository contracts and Riverpod providers only. It does not own storage or protocol logic.

Boundary rule:

- feature-specific mapping stays in the feature
- reusable external-service auth/session/orchestration belongs in an integration layer
- features may depend on integrations, but integrations must not depend on feature presentation state or feature-owned transport mappings they are meant to support

## Session separation
App auth, Matrix auth, and shared Nextcloud session handling are intentionally separate concerns:

- `auth/` owns the app-level OIDC session that decides whether the shell is reachable
- `chat/` owns Matrix protocol discovery, Matrix Native OAuth 2.0 login, refresh, logout, and SDK persistence
- `integrations/nextcloud/` consumes app-auth state when available, but owns Nextcloud bearer/app-password selection, secure Nextcloud session persistence, reconnect rules, and app-password revocation
- the app does not assume an app-level OIDC access token is also a Matrix access token
- the app does not assume an app-level OIDC token can be persisted as a raw Nextcloud bearer session; persisted Nextcloud bearer sessions are stored as tokenless markers and rehydrated from app auth state
- changing the Matrix homeserver invalidates the Matrix session without redesigning bootstrap
- changing the configured Nextcloud base URL invalidates the persisted Nextcloud session without requiring feature-owned cleanup logic

Matrix E2EE state also stays inside `features/chat/`:

- the Matrix crypto runtime is wired in the chat-owned Matrix client
- bootstrap, trust, verification, and recovery state are mapped to Weave-owned chat models before UI consumes them
- settings may host chat-owned security UI, but other features must not depend on raw Matrix crypto objects
- recovery keys must be treated as external user-held material; local secure storage can help cache secrets, but reinstall/device-restore behavior differs across Android, iOS, and macOS and must not be overclaimed
- the Matrix SDK `getCryptoIdentityState()` is the primary initialized/connected signal for chat-owned bootstrap mapping
- verification state must stay chat-owned as well; SDK states such as `askSSSS` are surfaced as recovery/unlock prompts rather than exposed directly in widgets
- current verification support is limited to SAS emoji/numbers plus SSSS unlock; QR verification remains out of scope until the client explicitly supports QR methods end-to-end

The current Matrix integration uses:

- the configured Matrix homeserver URL from `ServerConfiguration`
- `Client.checkHomeserver(..., fetchAuthMetadata: true)` for capability discovery
- Matrix Native OAuth 2.0 when `/_matrix/client/v1/auth_metadata` is available
- a typed unsupported-configuration failure when the homeserver only exposes legacy login
- Matrix SDK crypto setup helpers for first-device bootstrap, recovery reconnect, and self-verification continuation

## Nextcloud integration split
Nextcloud is now split into:

- `integrations/nextcloud/` for shared auth, session, account validation, login-flow handling, revoke policy, provider wiring, and connection lifecycle orchestration
- `features/files/` for DAV directory browsing, file-entry mapping, and file-facing presentation/state

This keeps the current Files UX intact while making the same Nextcloud platform layer reusable for future Calendar or provider-adapter board work without importing `features/files/`.

## Calendar backend facade scope

Calendar is active shared-scheduling scope and remains wired through the Weave backend product facade rather than direct CalDAV. The product model is shared scheduling: workspace calendar, team calendars, and channel calendars/events/meeting threads. The backend currently exposes the first safe slice as `scope.type = "workspace"`: a shared Weave workspace calendar owned/provisioned through the backend actor. The frontend parses that scope metadata and labels the surface as the first shared workspace scope, not as a private-personal calendar.

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

The generated profile is per-user and Docker-oriented: it names the baseline profile/image, isolated workspace root, isolated agent directory, Docker network mode, plugin allowlist, tool allowlist, and the capability keys visible to the runtime. Disabled and policy-blocked users receive the same contract shape with `enabled=false` and impact-level posture.

Runtime provisioning remains fail-closed unless all three gates pass: the Weaver workspace category is enabled, the governed runtime generator is enabled, and the user's Weave capability profile grants `weaver.enabled`. Allowed runtime capabilities are the intersection of Weave policy grants and the admin runtime allowlist. exec and elevated surfaces stay disabled by default and require future constrained admin policy before they can appear.

Profile generation publishes a support-safe audit event so later runtime start and tool-use flows can prove who generated a profile, which policy produced it, and whether exec/elevated surfaces were absent.
