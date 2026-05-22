# Weave Architecture

## Overview
Weave uses a feature-first clean architecture with deterministic bootstrap before routing. App-level OIDC bootstrap is resolved before navigation, while protocol-specific or platform-specific code lives either inside the owning feature or in `lib/integrations/<integration>/` when the boundary is shared across multiple features.

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
- Calendar (feature-gated active scope; Teams-like workspace/team/channel calendar hierarchy)
- Tasks/Boards (feature-gated active scope; provider-neutral Weave model, not a Nextcloud Deck product dependency)
- Settings

## Weave Home and chat information architecture

`/chat` is the first Weave Home surface. It is intentionally more structured than a flat Matrix room list:

- **Favorites** collect pinned/favorited people, channels, and AI chats once that metadata is available.
- **Personal messages** contain direct human-to-human conversations.
- **Channels** contain team/topic rooms and remain the main collaboration spine for future channel workspaces.
- **AI chats** provide a distinct home for specialized assistant and agent chats instead of mixing them into ordinary DMs.

The first implementation slice keeps Matrix as the conversation source, classifies direct messages versus channels from existing room metadata, and renders honest placeholders for favorites and AI chats until backend/product metadata is ready. Future channel workspace tabs for files, boards/tasks, and calendar should attach to channel context rather than becoming unrelated top-level app islands.

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

Calendar is active shared-scheduling scope and remains wired through the Weave backend product facade rather than direct CalDAV. The product model is Teams-like: workspace/org calendar, team calendars, and channel calendars/events/meeting threads. The backend currently exposes the first safe slice as `scope.type = "workspace"`: a shared Weave workspace calendar owned/provisioned through the backend actor. The frontend parses that scope metadata and labels the surface as the first shared workspace scope, not as a private-personal calendar.

Private personal calendars are out of scope for the current product path. Frontend code must continue to fail through the backend facade and must not add a direct private-personal CalDAV fallback or secret-bearing client setup path.

## Onboarding and settings
Onboarding setup and Settings share:

- the same `ServerConfiguration` domain model
- the same repository
- the same derivation rules
- the same form controller logic

The UI differs by context:

- onboarding presents the config as a guided two-step flow
- settings presents the same data as an editable configuration section inside the main shell

## Accessibility expectations
Refactors in this area must preserve:

- minimum `48x48` touch targets
- semantics labels for icon-only affordances
- readable focus order in setup/settings
- clear announcements for bootstrap loading and retry states
