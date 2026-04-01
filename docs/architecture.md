# Weave Architecture

## Overview
Weave uses a feature-first clean architecture with deterministic bootstrap before routing. App-level OIDC bootstrap is resolved before navigation, while protocol-specific sessions such as Matrix remain inside their owning feature.

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
- Calendar
- Deck
- Settings

## Shared server configuration
`features/server_config/` owns the shared configuration model used by both onboarding and settings.

Persisted fields:

- `providerType`
- `oidcIssuerUrl`
- `matrixHomeserverUrl`
- `nextcloudBaseUrl`

Derivation rule:

- parse the issuer URL
- use the issuer host
- if the host has 3 or more labels, drop the first label
- derive:
  - `https://matrix.<base-domain>`
  - `https://nextcloud.<base-domain>`

Example:

- `https://auth.home.internal` becomes `https://matrix.home.internal` and `https://nextcloud.home.internal`

This is intentionally simple, explicit, and easy to change later. It is a convenience default, not a hard rule. Users can edit the derived values during setup and in Settings.

## Persistence split
Persistence is split by responsibility:

- `PreferencesStore` for harmless configuration and future non-sensitive flags
- `SecureStore` as the future boundary for tokens and secrets

This task does not add real secure storage yet. The interface exists so auth work can plug into a dedicated sensitive path later without refactoring unrelated features.

## Failure model
`AppFailure` is the shared app-level failure model used across bootstrap, storage, and repositories. Presentation should respond to `AppFailure` rather than raw package exceptions.

Current failure types:

- `bootstrap`
- `storage`
- `validation`
- `unknown`

## Feature layering
Each feature follows the same three layers:

- `presentation/`
- `domain/`
- `data/`

Current repository-first stub boundaries:

- `auth` -> `AuthSessionRepository` + `OidcClient`
- `chat` -> `ChatRepository` + `MatrixClient`
- `files` -> `FilesRepository` + `WebDavClient`
- `calendar` -> `CalendarRepository` + `CalDavClient`
- `deck` -> `DeckRepository` + `DeckClient`

Presentation depends on repository contracts and Riverpod providers only. It does not own storage or protocol logic.

## Session separation
App auth and Matrix auth are intentionally separate concerns:

- `auth/` owns the app-level OIDC session that decides whether the shell is reachable
- `chat/` owns Matrix protocol discovery, Matrix Native OAuth 2.0 login, refresh, logout, and SDK persistence
- the app does not assume an app-level OIDC access token is also a Matrix access token
- changing the Matrix homeserver invalidates the Matrix session without redesigning bootstrap

The current Matrix integration uses:

- the configured Matrix homeserver URL from `ServerConfiguration`
- `Client.checkHomeserver(..., fetchAuthMetadata: true)` for capability discovery
- Matrix Native OAuth 2.0 when `/_matrix/client/v1/auth_metadata` is available
- a typed unsupported-configuration failure when the homeserver only exposes legacy login

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
