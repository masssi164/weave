# Weave

Weave is an accessibility-first Flutter client for self-hosted collaboration stacks. It is being built around a repository-first, feature-first architecture so future protocol integrations can land behind stable domain boundaries instead of leaking transport logic into presentation code.

## Vision
Weave aims to unify the core mobile workflows for self-hosted environments.

Release 1 is intentionally narrower:

- identity through OIDC providers such as Authentik and Keycloak
- communication through Matrix
- files through Nextcloud-backed services

Calendar and Deck remain future product areas, not Release 1 promises.

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
- Infra-managed OIDC app client ID: `weave-app`
- Matrix homeserver URL
- Nextcloud base URL
- Backend API base URL

Defaults for Matrix, Nextcloud, and the backend API are derived from the issuer host using a homelab-friendly rule, but the user can override those values during setup and later in Settings.

App OIDC redirect handling is aligned to the infrastructure SSOT:

- Sign-in redirect URI: `com.massimotter.weave:/oauthredirect`
- Logout redirect URI: `com.massimotter.weave:/logout`

For local development stacks, Weave accepts `http://` issuer and service URLs in addition to `https://`.

For the default homelab convention, Weave assumes:

- OIDC issuer / auth provider: `https://auth.home.internal`
- Matrix homeserver: `https://matrix.home.internal`
- Canonical Nextcloud URL: `https://files.home.internal`
- Backend API base URL: `https://home.internal/api`

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
├── integrations/
│   └── nextcloud/    # Shared Nextcloud auth/session/platform boundary
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

Inside each shared integration:

- `presentation/` contains reusable Riverpod providers and composition for the integration stack
- `domain/` contains shared entities, failures, and service/repository contracts
- `data/` contains persistence, protocol clients, and integration-level orchestration

Today, Nextcloud auth, session persistence, login-flow handling, bearer fallback, and connection lifecycle live under `lib/integrations/nextcloud/`, while `features/files/` stays focused on DAV directory browsing and file-domain mapping.

See [docs/architecture.md](docs/architecture.md) for the detailed design notes.

## Release 1 boundary
The first public release only presents Chat, Files, and Settings in the main app shell.

Calendar and Deck code may still exist behind the scenes while those features are under construction, but they are not presented as release-ready surfaces.

## Matrix Chat
Chat is the first real post-auth product slice in the app shell:

- app-level OIDC auth still only controls bootstrap and shell access
- Matrix protocol auth is handled separately inside `features/chat/`
- chat restores its own Matrix session from the SDK database when available
- legacy-only homeservers without Matrix OAuth metadata currently fail with a clear unsupported message instead of falling back to password login

The current Matrix security foundation also includes:

- chat-owned E2EE bootstrap state, device/account trust state, key backup state, and encrypted-room readiness mapping
- first-device crypto identity setup through secret storage, cross-signing, and online key backup
- recovery reconnect for devices that know the account but have lost local crypto secrets
- self-verification via SAS emoji/numbers, including the Matrix SDK `askSSSS` path where verification must be continued with a recovery key or passphrase

This is intentionally still a security foundation, not full encrypted timeline/send-receive chat. The current product surface focuses on secure session setup, trust health, and recovery without leaking raw Matrix crypto types into presentation code.

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

## Running Integration Tests
Integration tests require a live local Weave stack, including the backend API and Keycloak OIDC provider. Start the stack from the `weave-infra` setup first, with local hostnames resolving to the stack and the local CA trusted by the machine or simulator running the tests.

The local stack writes reusable test settings to `weave-infra/weave-workspace/.generated/bootstrap.env` and mirrors them to `/tmp/weave-infra/weave-workspace/.generated/bootstrap.env` for the self-hosted GitHub runner path. `make integration-test` sources the repo-local file first, then falls back to the `/tmp` mirror. Use `WEAVE_BOOTSTRAP_ENV` when your infra checkout lives elsewhere.

Expected local hostnames include `weave.local`, `auth.weave.local`, `matrix.weave.local`, and `files.weave.local`.

Run against the default local stack:

```sh
cd ../weave-infra/weave-workspace
TF_VAR_create_test_user=true ./install.sh
cd ../../weave
make integration-test
```

Run against a different infra checkout:

```sh
WEAVE_BOOTSTRAP_ENV=../weave-infra/weave-workspace/.generated/bootstrap.env make integration-test
```

The GitHub Actions live-stack job runs on a dedicated `self-hosted`, `macOS`, `ARM64`, `weave-live` runner. The job bootstraps a fresh local stack, builds the backend image from the selected backend ref, then reads the generated bootstrap env so the Flutter tests consume the exact API/Auth/Matrix/Nextcloud endpoints that infra exposed.

Supported overrides:

- `WEAVE_BASE_URL`: base URL for the Weave backend API, defaulting to `https://weave.local/api`
- `WEAVE_OIDC_ISSUER_URL`: OIDC issuer URL, defaulting to `https://auth.weave.local/realms/weave`
- `WEAVE_OIDC_CLIENT_ID`: app OIDC client ID, defaulting to `weave-app`
- `WEAVE_NEXTCLOUD_BASE_URL`: canonical Nextcloud URL, defaulting to `files.<workspace-host>` (legacy `WEAVE_NEXTCLOUD_URL` is also accepted)
- `WEAVE_MATRIX_HOMESERVER_URL`: Matrix homeserver URL, defaulting to `matrix.<workspace-host>` (legacy `WEAVE_MATRIX_URL` is also accepted)
- `WEAVE_TEST_USERNAME`: username for the test account
- `WEAVE_TEST_PASSWORD`: password for the test account

### Validation
Run the full validation suite before opening a change:

1. `flutter pub get`
2. `flutter gen-l10n`
3. `dart run build_runner build --delete-conflicting-outputs`
4. `dart format --output=none --set-exit-if-changed .`
5. `flutter analyze --fatal-infos`
6. `flutter test`
