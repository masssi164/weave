# Developer handbook

This handbook is the practical entry point for contributing to the Weave Flutter client. It complements the repo README and links to deeper docs instead of repeating every contract.

## Repository role

Weave is the frontend repository in a three-repo product system:

| Repo | Frontend-facing responsibility |
| --- | --- |
| `weave` | Flutter mobile/desktop app shell, custom chat/files/settings UX, client validation, accessibility, deterministic screenshots. |
| `weave-backend` | Product API/BFF, auth/profile facade, files/calendar/boards facades, readiness, backend contract tests. |
| `weave-infra` | Local/dev stack, Caddy, Keycloak, Matrix/Synapse/MAS, Nextcloud, Docker/Terraform orchestration, live-stack smoke and E2E environment. |
| workspace `specs/` | Binding cross-repo product and architecture contracts when available beside the repos. |

Do not invent backend endpoints, public URLs, auth claims, or infra defaults in Flutter. If a change needs a contract change, update the relevant workspace spec first.

## Local setup

Prerequisites:

- Flutter SDK on the stable channel.
- Xcode/macOS or another Flutter-supported target for local app runs.
- `make`, Python 3, and the normal Dart/Flutter toolchain.
- A sibling `weave-infra` checkout only for live-stack validation.

Clone and prepare the client:

```sh
git clone https://github.com/masssi164/weave.git
cd weave
flutter pub get
flutter gen-l10n
dart run build_runner build --delete-conflicting-outputs
flutter run
```

The app accepts local development service URLs such as `https://api.weave.local/api`, `https://auth.weave.local`, `https://matrix.weave.local`, and `https://files.weave.local` when a live stack is available.

## Everyday Flutter workflow

Use the existing generated-code workflow before analysis or tests:

```sh
flutter pub get
flutter gen-l10n
dart run build_runner build --delete-conflicting-outputs
```

Then run the cheap PR-safe gate:

```sh
dart format --output=none --set-exit-if-changed .
flutter analyze --fatal-infos
flutter test
make offline-contract-test
```

`make offline-contract-test` runs the no-network contract path by forcing `WEAVE_OFFLINE_CONTRACT_ONLY=true` and blank test credentials.

## Generated code and l10n

- Do not hand-edit generated Dart output.
- Run `flutter gen-l10n` after localization changes.
- Run `dart run build_runner build --delete-conflicting-outputs` after model/provider/codegen changes.
- Keep user-facing strings localizable; do not add hard-coded English text in widgets when the surrounding feature already uses l10n.

## Architecture conventions

Weave uses feature-first clean architecture under `lib/features/<feature>/`:

- `presentation/` for screens, widgets, and UI composition.
- `presentation/providers/` for Riverpod providers, notifiers, and UI-facing controllers.
- `domain/` for entities, use cases, and repository contracts.
- `data/` for implementations, datasources, DTOs, and protocol adapters.

Shared protocol or platform code belongs under `lib/integrations/<integration>/` with the same layering. Features may depend on `core/`, reusable widgets, and integrations, but must not import another feature's `data/` layer directly.

For deeper structure, see [Architecture](architecture.md).

## Accessibility and i18n rules

Accessibility is release scope, not polish:

- Interactive controls need at least `48x48` logical touch targets.
- Icon-only actions need semantics labels.
- Complex UI needs predictable reading order.
- Error, empty, loading, and success states need plain-language copy.
- Do not communicate state by color alone.
- Keep keyboard and screen-reader paths in mind for setup, sign-in, chat, files, settings, and live-stack error states.

See [Accessibility Release Gate](accessibility-release-gate.md) for the current automated and manual evidence model.

## Acceptance contract workflow

Gherkin scenarios are product acceptance contracts. PR #201 made the live-stack evidence path and scenario mapping a CI-validated contract rather than decorative BDD.

When changing user journeys or live-stack behavior:

1. Read [Acceptance contracts](acceptance-contracts.md) and [Product acceptance flows](product-acceptance-flows.md).
2. Update or add the scenario first when target behavior changes.
3. Keep scenario language product-facing and implementation-neutral.
4. Update the scenario-to-test mapping so `test/live_stack_feature_mapping_test.dart` stays green.
5. Add or adjust Flutter tests and live-stack E2E coverage as appropriate.
6. Record what was validated in the PR body.

## Offline vs live-stack validation

Use the cheapest sufficient gate by default:

| Layer | Command or workflow | When to use |
| --- | --- | --- |
| Format/analyze/unit | `dart format --output=none --set-exit-if-changed .`, `flutter analyze --fatal-infos`, `flutter test` | Normal Flutter changes. |
| Offline contract | `make offline-contract-test` | PR-safe contract and mapping checks without real credentials. |
| Marketing assets | `make marketing-screenshots` plus `git diff --exit-code -- docs/assets/marketing docs/assets/roadmap` | README/docs screenshot changes. |
| Live contract | `make integration-contract-test` | Backend/auth/service contract changes with real test credentials. |
| App E2E | `make integration-app-e2e` or `make integration-test` | Expensive app-level live-stack validation. |
| CI live stack | `.github/workflows/live-stack-e2e.yml` | Manual/self-hosted runner evidence with uploaded artifacts. |

Live-stack commands require a prepared `weave-infra` stack and real test credentials. Do not commit credentials, generated secret files, screenshots containing secrets, or raw live logs with sensitive values.

## Quality and evidence artifacts

Use [Quality and acceptance evidence](quality-and-evidence.md) as the landing page for release evidence. It explains offline checks, screenshot drift, acceptance contract artifacts, live-stack uploads, and how to interpret failures without relying on stale workflow run IDs.

## Marketing screenshots

README images are deterministic SVGs generated by:

```sh
make marketing-screenshots
```

The generator is `tool/generate_marketing_screenshots.py`.

- Main product showcase assets live in `docs/assets/marketing/`.
- Calendar and boards/tasks guarded roadmap visuals live in `docs/assets/roadmap/`.
- Keep SVG `<title>` and `<desc>` meaningful.
- Keep README and docs `alt` text descriptive.
- Regenerate and review the SVG diff whenever copy, filenames, or visuals change.

## Documentation changes without drift

- Link existing docs instead of duplicating long contracts.
- Avoid absolute local filesystem paths.
- Use stable workflow and docs links, not one-off run IDs, for permanent evidence claims.
- Keep README claims tied to implemented or explicitly gated behavior.
- If docs change setup, contract behavior, public URLs, or live-stack expectations, check the matching workspace spec before editing code.

## PR checklist

Before requesting review, include:

- Scope and user/developer impact.
- Contract impact, including whether specs changed.
- Accessibility and localization notes when UI-facing.
- Screenshot/docs impact when user-visible.
- Commands run and results.
- Any live-stack checks skipped and why.
