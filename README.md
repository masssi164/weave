# Weave

Weave is an accessibility-first Flutter client for self-hosted collaboration stacks. It is being built around a repository-first, feature-first architecture so future protocol integrations can land behind stable domain boundaries instead of leaking transport logic into presentation code.

## Vision
Weave aims to unify the core mobile workflows for self-hosted environments:

- Identity through OIDC providers such as Authentik and Keycloak
- Communication through Matrix
- Files, calendars, and deck-style planning through Nextcloud-backed services

## Current foundation
The app now starts through an explicit bootstrap phase before the router is built. Startup resolves into one of:

- `loading`
- `needsSetup`
- `needsSignIn`
- `ready`
- `error`

That means routing no longer depends on a temporary default that flips later from storage.

Setup and Settings now share one persisted server configuration model:

- OIDC provider type
- OIDC issuer URL
- Matrix homeserver URL
- Nextcloud base URL

Defaults for Matrix and Nextcloud are derived from the issuer host using a simple homelab-friendly rule, but the user can override those values during setup and later in Settings.

For the default homelab convention, Weave assumes:

- OIDC issuer / auth provider: `https://auth.home.internal`
- Matrix homeserver: `https://matrix.home.internal`

## Architecture
Weave follows a feature-first clean architecture layout:

```text
lib/
├── core/
│   ├── bootstrap/    # App start resolution before routing
│   ├── failures/     # Shared app-level error model
│   ├── persistence/  # Secure/non-secure storage boundaries
│   ├── router/       # go_router setup and route constants
│   ├── theme/
│   └── widgets/
└── features/
    ├── auth/
    ├── calendar/
    ├── chat/
    ├── deck/
    ├── files/
    ├── onboarding/
    ├── server_config/
    └── settings/
```

Inside each feature:

- `presentation/` contains screens, widgets, and Riverpod UI state
- `domain/` contains entities and repository contracts
- `data/` contains repository implementations, persistence adapters, DTOs, and protocol/service clients

See [docs/architecture.md](docs/architecture.md) for the detailed design notes.

## Matrix Chat
Chat is the first real post-auth product slice in the app shell:

- app-level OIDC auth still only controls bootstrap and shell access
- Matrix protocol auth is handled separately inside `features/chat/`
- chat restores its own Matrix session from the SDK database when available
- legacy-only homeservers without Matrix OAuth metadata currently fail with a clear unsupported message instead of falling back to password login

## Accessibility
Accessibility is a hard requirement, not a follow-up:

- interactive targets must be at least `48x48`
- icon-only affordances must expose semantics labels
- complex layouts must keep a predictable reading order
- setup, settings, shell navigation, and shared states must remain screen-reader friendly during refactors

## Development
### Prerequisites
- [Flutter SDK](https://docs.flutter.dev/get-started/install)

### Run locally
1. `flutter pub get`
2. `flutter run`

### Validation
Run the full validation suite before opening a change:

1. `flutter pub get`
2. `flutter gen-l10n`
3. `dart run build_runner build --delete-conflicting-outputs`
4. `dart format --output=none --set-exit-if-changed .`
5. `flutter analyze --fatal-infos`
6. `flutter test`
