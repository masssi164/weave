# Weave

<p align="center">
  <img src="assets/images/weave_logo.png" alt="Weave logo: a woven blue and teal product mark" width="320">
</p>

<p align="center">
  <a href="https://github.com/masssi164/weave/actions/workflows/ci.yml"><img src="https://github.com/masssi164/weave/actions/workflows/ci.yml/badge.svg" alt="CI workflow status"></a>
  <a href="https://github.com/masssi164/weave/actions/workflows/live-stack-e2e.yml"><img src="https://github.com/masssi164/weave/actions/workflows/live-stack-e2e.yml/badge.svg" alt="Live Stack E2E workflow status"></a>
</p>

Weave is an accessibility-first collaboration client for teams that want modern work tools without giving up data sovereignty. It brings setup, sign-in, chat, files, and workspace settings into one Flutter app backed by self-hosted services such as Matrix, Nextcloud, Keycloak, and the Weave backend.

The goal is simple: give teams and organizations a humane migration path away from closed team suites while keeping the product experience cohesive, professional, and understandable for admins and end users.

## Why Weave

- **Accessibility built into the core** — critical flows are designed around large touch targets, semantic labels, keyboard/screen-reader behavior, and clear error states. Formal accessibility claims should stay tied to documented audits and evidence.
- **Data-sovereign collaboration** — Matrix and Nextcloud provide open protocol/data foundations behind a Weave-owned user experience.
- **One product, not separate islands** — sign-in, profile, navigation, diagnostics, chat, files, and settings are designed to feel like one workspace.
- **Migration-friendly** — Slack and Microsoft Teams interop are planned as controlled backend-owned migration and bridge paths, not as client-side shortcuts.
- **Built for later personal agents** — the long-term direction includes Weaver PA: an OpenClaw-style per-user agent runtime with organization-governed skills, connectors, and group-chat agents. This is later scope, not part of the current active product track.

## Current status

Weave is on an active product-maturity track. The showcased client surfaces are the parts that contributors can evaluate directly today: guided setup, service endpoint review, custom chat, basic files, and workspace settings. Calendar, Matrix E2EE completion, boards/tasks, production bridges, public connector SDKs, and Weaver PA remain behind explicit contracts, feature gates, or later roadmap boundaries until their evidence is complete.

The live-stack contract now proves selected end-to-end behavior through CI artifacts, while the default pull-request path stays offline and inexpensive. See [Quality and acceptance evidence](docs/quality-and-evidence.md) for what each gate proves and how to interpret failures.

## Product screenshots

A first look at the current product-maturity experience: guided setup, service review, custom chat, basic files, and workspace settings in one self-hosted product shell. These screenshots are deterministic SVGs generated from checked-in source, so the README stays reviewable and reproducible without turning documentation into image-only content.

### Setup and service review

[<img src="docs/assets/marketing/01-setup-start.svg" alt="Weave setup start screen showing a guided workspace setup path and canonical local service URLs." width="560">](docs/assets/marketing/01-setup-start.svg)

[<img src="docs/assets/marketing/02-review-service-endpoints.svg" alt="Weave setup endpoint review screenshot listing Matrix, files, and backend service URLs before finishing setup." width="560">](docs/assets/marketing/02-review-service-endpoints.svg)

### Daily collaboration

[<img src="docs/assets/marketing/03-chat-room.svg" alt="Weave chat room screenshot showing the Release Room, message history, and a send message action." width="560">](docs/assets/marketing/03-chat-room.svg)

[<img src="docs/assets/marketing/04-files-documents.svg" alt="Weave files screenshot showing the Documents folder with folders, files, and accessible file actions." width="560">](docs/assets/marketing/04-files-documents.svg)

### Workspace settings

[<img src="docs/assets/marketing/05-settings.svg" alt="Weave settings screenshot showing OIDC issuer, client ID, Nextcloud URL, and account session controls." width="560">](docs/assets/marketing/05-settings.svg)

Regenerate screenshots with `make marketing-screenshots` and review the SVG diff before committing. Calendar and boards/tasks are documented separately in [Roadmap and guarded surfaces](docs/roadmap-and-guarded-surfaces.md) so the main showcase does not overclaim unfinished product areas.

## Quick start for contributors

Start with the [Developer handbook](docs/developer-handbook.md) for the complete workflow. The minimal offline path is:

```sh
flutter pub get
flutter gen-l10n
dart run build_runner build --delete-conflicting-outputs
dart format --output=none --set-exit-if-changed .
flutter analyze --fatal-infos
flutter test
make offline-contract-test
```

Use [`weave-infra`](https://github.com/masssi164/weave-infra) only when a change requires live-stack validation. The expensive live path and its acceptance artifacts are described in [Quality and acceptance evidence](docs/quality-and-evidence.md) and [Acceptance contracts](docs/acceptance-contracts.md).

## Roadmap honesty

Weave does **not** claim a complete Teams/Slack replacement, public connector SDK, production Slack/Teams bridge, private personal calendar provisioning, completed Matrix E2EE UX, or Weaver PA. Current roadmap surfaces and gated evidence are kept in [Roadmap and guarded surfaces](docs/roadmap-and-guarded-surfaces.md) and [Product scope: calendar hierarchy, Matrix E2EE, and Boards](docs/product-calendar-e2ee-boards-scope.md).

## Product architecture

Weave is the Flutter client in a three-repository product system:

| Repository | Owns |
| --- | --- |
| [`weave`](https://github.com/masssi164/weave) | Flutter mobile/desktop client, app shell, custom chat/files/settings UX, accessibility, and client-side tests. |
| [`weave-backend`](https://github.com/masssi164/weave-backend) | Spring Boot product API/BFF, auth validation, profile facade, files/calendar facades, readiness, and backend contracts. |
| [`weave-infra`](https://github.com/masssi164/weave-infra) | Local/dev stack, Caddy routing, Keycloak, Matrix/Synapse/MAS, Nextcloud, Terraform, Docker orchestration, smoke/E2E environment. |
| `specs/` (workspace sibling) | Binding cross-repo product and architecture contracts when this repository is checked out in the full workspace. |

The Weave product should feel like one collaboration platform even though sovereign modules sit behind it:

- **Keycloak** is the identity authority.
- **Weave backend** is the product-facing API/BFF after sign-in.
- **Matrix** provides chat protocol and storage.
- **Nextcloud** provides files and calendar data foundations behind product-owned Weave surfaces.
- **Caddy** exposes the local/dev HTTPS gateway.

For the detailed Flutter architecture, see [docs/architecture.md](docs/architecture.md). The binding active product scope for Teams-like Calendar, Matrix E2EE, and Boards is [docs/product-calendar-e2ee-boards-scope.md](docs/product-calendar-e2ee-boards-scope.md). For the later interop direction, see [docs/interop-gateway-and-external-collaboration.md](docs/interop-gateway-and-external-collaboration.md). For boards/tasks provider research, see [docs/research/boards-task-module-provider-strategy.md](docs/research/boards-task-module-provider-strategy.md) and [docs/research/boards-task-domain-contract.md](docs/research/boards-task-domain-contract.md).

## Current client foundation

The app starts through an explicit bootstrap phase before the router is built. Startup resolves into one of:

- `loading`
- `needsSetup`
- `needsSignIn`
- `ready`
- `error`

Setup and Settings share one persisted server configuration model:

- OIDC provider type
- OIDC issuer URL
- infra-managed OIDC app client ID, defaulting to `weave-app`
- Matrix homeserver URL
- Nextcloud/files base URL
- backend API base URL

App OIDC redirect handling is aligned to the infrastructure contract:

- sign-in redirect URI: `com.massimotter.weave:/oauthredirect`
- logout redirect URI: `com.massimotter.weave:/logout`

For local development stacks, Weave accepts `http://` issuer and service URLs in addition to `https://`.

Default local/dev surfaces are:

| Surface | URL |
| --- | --- |
| Product shell/admin entry | `https://weave.local` |
| Backend API/BFF | `https://api.weave.local/api` |
| Auth | `https://auth.weave.local` |
| Matrix | `https://matrix.weave.local` |
| Raw Nextcloud/admin/protocol fallback | `https://files.weave.local` |

## Code layout

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
├── integrations/     # Shared external-service/platform boundaries
└── features/
    ├── auth/
    ├── chat/
    ├── files/
    ├── calendar/
    ├── onboarding/
    ├── server_config/
    └── settings/
```

Inside each feature:

- `presentation/` contains screens, widgets, and Riverpod UI state.
- `domain/` contains entities and repository contracts.
- `data/` contains repository implementations, persistence adapters, DTOs, and protocol/service clients.

Shared integrations use the same layering under `lib/integrations/<integration>/` when multiple features need the same protocol or platform boundary.

## Accessibility baseline

Accessibility is a release requirement, not polish:

- interactive targets must be at least `48x48` logical pixels;
- icon-only actions must expose semantics labels;
- complex layouts must keep a predictable reading order;
- setup, sign-in, shell navigation, chat, files, settings, and error states must remain screen-reader friendly;
- user-facing failures should be plain-language and actionable.

## Development

For the full contributor workflow, read the [Developer handbook](docs/developer-handbook.md). It covers local setup, generated code, l10n, clean architecture conventions, accessibility, acceptance evidence, screenshot generation, and cross-repo boundaries.

### Prerequisites

- [Flutter SDK](https://docs.flutter.dev/get-started/install)
- A local Weave stack from [`weave-infra`](https://github.com/masssi164/weave-infra) for live integration and E2E tests

### Run locally

```sh
flutter pub get
flutter run
```

### Lightweight validation

```sh
flutter pub get
flutter gen-l10n
dart run build_runner build --delete-conflicting-outputs
dart format --output=none --set-exit-if-changed .
flutter analyze --fatal-infos
flutter test
make offline-contract-test
```

### Marketing screenshots

Marketing/README screenshots are deterministic SVG assets generated from a small checked-in script, not pixel-perfect goldens. This keeps normal PR validation focused on behavior while still making docs imagery reproducible and reviewable.

```sh
make marketing-screenshots
```

The command regenerates the product-maturity setup, endpoint review, chat, files, and settings images in `docs/assets/marketing/`, plus guarded roadmap visuals in `docs/assets/roadmap/`. CI runs the generator and fails if the checked-in assets drift. In GitHub Actions, run the `CI` workflow manually with `capture_marketing_screenshots=true` to also download the `weave-marketing-screenshots` artifact.

When adding selected images to the README or docs, keep nearby prose and descriptive `alt` text so the documentation is not image-only.

## Live stack and integration tests

Integration tests require a live local Weave stack, including the backend API and Keycloak OIDC provider. Start the stack from the `weave-infra` setup first, with local hostnames resolving to the stack and the local CA trusted by the machine or simulator running the tests.

The local stack writes reusable test settings to `weave-infra/weave-workspace/.generated/bootstrap.env`. The Make targets source that repo-local file by default. Use `WEAVE_BOOTSTRAP_ENV` when your infra checkout lives elsewhere; there is no implicit global `/tmp` fallback.

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

The GitHub Actions live-stack path runs on a dedicated `self-hosted`, `macOS`, `ARM64`, `weave-live` runner and is manual-only through `workflow_dispatch` or explicit workflow reuse. Dispatch requires confirmation that the runner has enough power, storage, and maintenance budget.

Useful test targets:

- `make offline-contract-test` — lightweight automatic/no-network contract gate.
- `make integration-contract-test` — live-stack contract check requiring real test credentials.
- `make integration-app-e2e` / `make integration-test` — expensive app-level live E2E targets for manual runs.

Gherkin scenarios are product acceptance contracts, not decorative BDD. See
[docs/acceptance-contracts.md](docs/acceptance-contracts.md) for the ATDD workflow,
scenario-to-test mapping guard, and live-stack acceptance artifact contract.

Supported overrides:

- `WEAVE_API_BASE_URL`: canonical base URL for the Weave backend API, defaulting to `https://api.weave.local/api`
- `WEAVE_BASE_URL`: legacy-compatible alias for `WEAVE_API_BASE_URL`
- `WEAVE_OIDC_ISSUER_URL`: OIDC issuer URL, defaulting to `https://auth.weave.local/realms/weave`
- `WEAVE_OIDC_CLIENT_ID`: app OIDC client ID, defaulting to `weave-app`
- `WEAVE_NEXTCLOUD_BASE_URL`: canonical Nextcloud URL, defaulting to `files.<workspace-host>`; legacy `WEAVE_NEXTCLOUD_URL` is also accepted
- `WEAVE_MATRIX_HOMESERVER_URL`: Matrix homeserver URL, defaulting to `matrix.<workspace-host>`; legacy `WEAVE_MATRIX_URL` is also accepted
- `WEAVE_TEST_USERNAME`: username for the test account
- `WEAVE_TEST_PASSWORD`: password for the test account

## Status and roadmap honesty

Weave is under active development. The active product track focuses on making the core client shell, chat, files, recovery states, Teams-like calendar, Matrix E2EE architecture, and boards/tasks honest and product-ready behind clear gates before broadening the surface. The roadmap is ambitious, but README claims should stay tied to implemented or explicitly future-scoped capabilities.
