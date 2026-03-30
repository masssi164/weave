# Weave Architecture

## Overview
Weave uses a feature-first clean architecture with deterministic bootstrap before routing. The current foundation is intentionally integration-light: protocol clients and repositories are stubbed, but the boundaries are real so future Authentik, Keycloak, Matrix, Nextcloud, WebDAV, CalDAV, and Deck work can land without reworking presentation.

## App startup
The app now resolves bootstrap before `MaterialApp.router` is built.

Bootstrap phases:

- `loading`
- `needsSetup`
- `ready`
- `error`

Source of truth:

- a valid persisted `ServerConfiguration` means `ready`
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
