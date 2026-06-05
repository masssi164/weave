# Weave

<p align="center">
  <img src="assets/images/weave_logo.png" alt="Weave logo: a woven blue and teal product mark" width="320">
</p>

<p align="center">
  <a href="https://github.com/masssi164/weave/actions/workflows/ci.yml"><img src="https://github.com/masssi164/weave/actions/workflows/ci.yml/badge.svg" alt="CI workflow status"></a>
  <a href="https://github.com/masssi164/weave/actions/workflows/live-stack-e2e.yml"><img src="https://github.com/masssi164/weave/actions/workflows/live-stack-e2e.yml/badge.svg" alt="Live Stack E2E workflow status"></a>
</p>

**Accessible collaboration, under your control.**

Weave is a self-hosted collaboration workspace for teams that need modern daily work tools without handing their data, identity, or accessibility standards to a closed suite. The Flutter app turns Matrix, Nextcloud, Keycloak, and the Weave backend into one coherent product experience for setup, sign-in, chat, files, readiness, and workspace settings.

Weave is not a raw bundle of provider UIs and it is not claiming to be a finished Slack or Microsoft Teams clone. It is an active product-maturity build with honest feature gates: release surfaces are only shown as shipped when they have executable evidence, and unsafe provider paths fail closed.

## What Weave is for

- Privacy-sensitive teams, clubs, small organizations, public-sector/NGO/health/education groups, and self-hosters who want one polished front door over open collaboration services.
- Operators who need repeatable deployment, health checks, readiness states, backups, and support diagnostics without exposing secrets.
- Users who need an accessible workspace shell with clear recovery states instead of scattered admin/protocol screens.

## Product pillars

- **Accessible workspace shell:** setup, sign-in, navigation, settings, recovery states, semantic labels, keyboard/screen-reader-friendly flows, and non-color-only status.
- **Sovereign collaboration:** Matrix-backed chat and Nextcloud-backed files/calendar foundations, presented through Weave-owned UX instead of raw provider screens.
- **Backend-owned provider boundary:** Flutter talks to `weave-backend` product APIs. It does not call GitLab, Forgejo, OpenProject, ONLYOFFICE, Collabora, Nextcloud admin APIs, or other provider runtimes directly.
- **Honest readiness:** provider status, capability snapshots, degraded states, and fail-closed errors are visible without leaking backend actor tokens, provider URLs, raw errors, or secrets.
- **Operator-grade validation:** offline checks stay cheap for normal PRs; live-stack E2E runs only when the full stack and runner budget are explicitly available.

## Available now

The current app lets contributors evaluate these product surfaces directly:

- guided workspace setup and service endpoint review;
- OIDC sign-in and persisted server configuration;
- custom Matrix chat shell with explicit recovery/retry states;
- backend-facade files browsing and actions;
- settings/profile/session controls;
- workspace, Matrix E2EE, and provider-stack readiness views that stay support-safe.

## Gated scope and non-goals

These areas are active product scope, but the app must keep them fail-closed until the backend facade, permission, audit, accessibility, and evidence gates are ready:

- **Shared calendars:** workspace, team, and channel scheduling through backend facades. Private personal calendar ingestion is not a product goal.
- **Boards/tasks:** provider-neutral Weave UX and backend contracts with explicit user writes, authorization, audit, support-safe errors, and non-drag task work.
- **Meetings/video calls:** LiveKit is the provider contract; join/start remain fail-closed until backend token, media, metadata, and encryption evidence gates are configured and validated.
- **Matrix E2EE:** active architecture path, not a completed claim. Weave must validate encrypted rooms, device verification, key backup/recovery, multi-device behavior, metadata boundaries, and accessibility before claiming production readiness.
- **Interop/connectors:** Slack/Teams/connector routes default off and remain backend-owned. No client-side provider shortcuts.
- **Personal agents/automation:** later roadmap, not README hero scope.

## Product screenshots

A first look at the active product-maturity experience: guided setup, service review, custom chat, basic files, and workspace settings in one self-hosted product shell. These screenshots are deterministic SVGs generated from checked-in source, so the README stays reviewable and reproducible without turning documentation into image-only content.

### Setup and service review

[<img src="../docs/assets/marketing/01-setup-start.svg" alt="Weave setup start screen showing a guided workspace setup path and canonical local service URLs." width="560">](../docs/assets/marketing/01-setup-start.svg)

[<img src="../docs/assets/marketing/02-review-service-endpoints.svg" alt="Weave setup endpoint review screenshot listing Matrix, files, and backend service URLs before finishing setup." width="560">](../docs/assets/marketing/02-review-service-endpoints.svg)

### Daily collaboration

[<img src="../docs/assets/marketing/03-chat-room.svg" alt="Weave chat room screenshot showing the Release Room, message history, and a send message action." width="560">](../docs/assets/marketing/03-chat-room.svg)

[<img src="../docs/assets/marketing/04-files-documents.svg" alt="Weave files screenshot showing the Documents folder with folders, files, and accessible file actions." width="560">](../docs/assets/marketing/04-files-documents.svg)

### Workspace settings

[<img src="../docs/assets/marketing/05-settings.svg" alt="Weave settings screenshot showing OIDC issuer, client ID, Nextcloud URL, and account session controls." width="560">](../docs/assets/marketing/05-settings.svg)

Regenerate screenshots with `make marketing-screenshots` and review the SVG diff before committing. Additional gated surfaces are documented in [Roadmap and guarded surfaces](../docs/roadmap-and-guarded-surfaces.md) so the main showcase does not overclaim unfinished product areas.

## Monorepo architecture

Weave is developed as one monorepo with dedicated product-stack directories:

- `../client`: Flutter app, app shell, accessibility, chat/files/settings UX, provider-readiness presentation, and app tests.
- `../server`: Spring Boot product API/BFF, JWT validation, profile/files/calendar/provider facades, readiness, support-safe errors, audit seams, and backend contracts.
- `../infra`: Docker/OpenTofu stack, Caddy routing, Keycloak, Matrix/Synapse/MAS, Nextcloud, optional provider runtimes, backups, smoke checks, and live E2E environment.
- `../e2e`: product-language Gherkin, scenario mapping, and sanitized evidence contracts.
- `../release`: stack manifests and release metadata.

Responsibility split:

- Keycloak owns identity.
- Matrix owns chat protocol and Matrix-native auth/E2EE foundations.
- Nextcloud owns files/calendar storage foundations.
- Weave backend owns product APIs, readiness, server-side facades, secret boundaries, error envelopes, audit/consent seams, and provider gating.
- Weave Flutter owns the daily product experience and must stay on backend-owned product contracts.
- Caddy and infrastructure own routing, TLS, deployment, smoke checks, backups, restore smoke, and support diagnostics.

For details, see:

- [Flutter architecture](docs/architecture.md)
- [Developer handbook](docs/developer-handbook.md)
- [Quality and acceptance evidence](docs/quality-and-evidence.md)
- [Acceptance contracts](docs/acceptance-contracts.md)
- [Roadmap and guarded surfaces](../docs/roadmap-and-guarded-surfaces.md)

## Local development

```sh
flutter pub get
flutter run
```

### App start / member join contract

Weave has one member startup flow: open a support-safe `/join` link, load public `/api/platform/config`, save OIDC/API/facade configuration, start SSO, then show the workspace home.

- Production/customer entry: `https://<weave-link-domain>/join?...` via iOS Universal Links / Android App Links where the domain is in the app association contract.
- Local iPhone dogfood: use the same `/join` contract when possible; `weave:/join?...` is only the local-dev fallback when Universal Link verification cannot work for IP/self-signed setups.
- Do not reinstall the app during startup. Keep the stable bundle/package identity and OIDC redirects: `com.massimotter.weave:/oauthredirect` and `com.massimotter.weave:/logout`.
- The member app must not ask users for Matrix/Nextcloud/provider URLs, secrets, diagnostics, or admin control-plane details.
- Local self-signed HTTPS still requires installing/trusting the local CA on the device because Safari/AppAuth use system trust.

The app-link association artifacts live in `client/app-links/`. Android release App Links require replacing the template fingerprint with the release signing certificate fingerprint before publishing.

### Android identity and release signing

Android debug builds use the Weave package id `com.massimotter.weave`; the OIDC redirect scheme stays `com.massimotter.weave`, so app-auth provider configuration must still allow `com.massimotter.weave:/oauthredirect` and `com.massimotter.weave:/logout` after package-id changes.

Release artifacts are intentionally fail-safe. The Android release build does not fall back to debug keys. To build a signed release artifact, create a local, untracked `client/android/key.properties` file:

```properties
storeFile=/secure/path/weave-release.jks
storePassword=...
keyAlias=weave-release
keyPassword=...
```

Then run:

```sh
flutter build appbundle --release
```

Without that complete file and keystore, release artifact tasks fail with an explicit signing-credentials error; use `flutter run` or debug/profile builds for local development.

Full lightweight validation:

```sh
flutter pub get
flutter gen-l10n
dart run build_runner build --delete-conflicting-outputs
dart format --output=none --set-exit-if-changed .
flutter analyze --fatal-infos
flutter test
make offline-contract-test
```

## Live stack and E2E

Use `../infra` when a change needs real Keycloak, Matrix, Nextcloud, backend, or provider-stack evidence.

Default local stack flow:

```sh
cd ../infra/weave-workspace
TF_VAR_create_test_user=true ./install.sh
cd ../../client
make integration-test
```

The local stack writes reusable test settings to `../infra/weave-workspace/.generated/bootstrap.env`. Override the path when using another checkout:

```sh
WEAVE_BOOTSTRAP_ENV=../infra/weave-workspace/.generated/bootstrap.env make integration-test
```

Useful targets:

- `make offline-contract-test`: automatic no-network contract gate.
- `make integration-contract-test`: live-stack contract check requiring real test credentials.
- `make integration-app-e2e` / `make integration-test`: expensive app-level live E2E targets for manual runs.
- `make marketing-screenshots`: regenerate README/roadmap SVG assets.

The GitHub Actions live-stack path runs on a dedicated self-hosted macOS ARM64 runner and is manual-only. Dispatch requires confirmation that the runner has enough power, storage, and maintenance budget.

## Accessibility baseline

Accessibility is a release gate, not polish:

- interactive targets are at least `48x48` logical pixels;
- icon-only actions expose semantic labels;
- complex layouts keep predictable reading order;
- setup, sign-in, shell navigation, chat, files, settings, readiness, and error states remain screen-reader friendly;
- status is never conveyed by color alone;
- user-facing failures are plain-language and actionable.

## Code layout

```text
lib/
├── core/             # bootstrap, failure model, persistence, router, theme, shared widgets
├── integrations/     # reusable backend/platform boundaries
└── features/         # auth, chat, files, calendar, onboarding, settings, app shell
```

Inside each feature:

- `presentation/`: screens, widgets, and Riverpod UI state.
- `domain/`: entities and repository contracts.
- `data/`: repositories, persistence adapters, DTOs, and service clients.

Shared integrations follow the same layering under `lib/integrations/<integration>/` when multiple features need one protocol or platform boundary.
