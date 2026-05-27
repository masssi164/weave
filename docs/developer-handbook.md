# Developer handbook

This handbook is the practical entry point for contributing to the Weave monorepo, especially the Flutter client. It complements the repo README and links to deeper docs instead of repeating every contract.

## Repository role

Weave is now a monorepo product stack:

| Directory | Responsibility |
| --- | --- |
| `client/` | Flutter mobile/desktop app shell, custom chat/files/settings UX, client validation, accessibility, deterministic screenshots. |
| `server/` | Product API/BFF, auth/profile facade, files/calendar/boards facades, authorization, audit, readiness, and backend contract tests. |
| `infra/` | Local/dev stack, Caddy, Keycloak, Matrix/Synapse/MAS, Nextcloud, Docker/OpenTofu orchestration, live-stack smoke and E2E environment. |
| `e2e/` | Binding product-language Gherkin scenarios, scenario mapping, and sanitized evidence contract. |
| `release/` | Stack manifests and release compatibility metadata. |

Do not invent backend endpoints, public URLs, auth claims, or infra defaults in Flutter. If a change needs a contract change, update `server/`, `infra/`, `e2e/`, and the relevant docs in the same monorepo change.

## Local setup

Prerequisites:

- Flutter SDK on the stable channel.
- Xcode/macOS or another Flutter-supported target for local app runs.
- `make`, Python 3, Java 21+ for Gradle/backend checks, and the normal Dart/Flutter toolchain.
- Node/npm for admin console checks.
- Docker/OpenTofu only for live-stack validation.

On macOS with Homebrew JDK 21, use the same Java line as CI before running Gradle gates:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export PATH="$JAVA_HOME/bin:$PATH"
java -version
```

Clone the monorepo and prepare the client:

```sh
git clone https://github.com/masssi164/weave.git
cd weave/client
flutter pub get
flutter gen-l10n
dart run build_runner build --delete-conflicting-outputs
flutter run
```

The app accepts local development service URLs such as `https://api.weave.local/api`, `https://auth.weave.local`, `https://matrix.weave.local`, and `https://files.weave.local` when a live stack is available.


## Root Gradle orchestration

The root `./gradlew` is the monorepo build/delivery source of truth. GitHub Actions installs pinned ecosystem dependencies and runs `./gradlew ci`; Make targets are temporary compatibility aliases that delegate to Gradle during the transition. Do not add new root Make-only build logic.

| Gradle task | Purpose |
| --- | --- |
| `doctor` | Checks required tools and pinned dependency files with actionable failures. |
| `acceptanceContract` | Gherkin mapping and acceptance contract guard. |
| `clientCi` | Flutter generated-code, format, analysis, tests, screenshot drift, and offline contract path. |
| `serverCi` | Existing server Gradle test path. |
| `adminCi` | Admin console npm CI path. |
| `infraStatic` | OpenTofu format/validate plus infrastructure script/static checks. |
| `docsBuild` | Strict MkDocs build with deterministic outputs under `build/docs/user` and `build/docs/admin`. |
| `docsCheck` | Docs structure check plus strict MkDocs build. |
| `releaseNotesLabelCheck` | Current PR release-notes label validation when `PR_LABELS_JSON` is available; skipped locally when unset. |
| `releaseEvidenceCheck` | Release notes structure, README markers, label behavior, and generator fixture checks. |
| `releaseNotesCheck` | Compatibility alias for `releaseEvidenceCheck`. |
| `ci` | Canonical aggregate for the PR-safe monorepo gate set. |

Each task requires the same tools and dependency setup as the underlying ecosystem command; for example docs tasks need pinned dependencies installed in `build/docs-venv` or the active Python environment from `docs/requirements.txt`, server checks need Java 21+, client checks need Flutter/Dart, and admin checks need Node/npm dependencies. `./gradlew ci` writes sanitized evidence to `build/evidence/ci-summary.json`; CI uploads `build/evidence/**` and deterministic docs outputs as artifacts. If local `./gradlew doctor` reports JDK 17, point `JAVA_HOME` at JDK 21+ rather than weakening the gate.

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

## Trunk-based PR and release workflow

Use protected `main` plus short-lived PR branches; do not introduce long-lived `dev`, `develop`, `testing`, `staging`, or `release/*` branches as the primary flow. Keep changes issue/spec-driven and request Copilot review on every review-ready PR. Every PR must deliberately choose exactly one release-notes label before review/merge:

- `release-notes-feature`
- `release-notes-bugfix`
- `release-notes-skip`

Release notes are generated from merged PR labels, not manually reconstructed later. The CI `Release Notes Label Check` runs on every pull-request update and fails PRs with zero or multiple release-notes labels; label-only changes run that lightweight check without re-running the full Gradle CI job. See [Weave operating model](weave-operating-model.md) for the delivery contract and [Trunk-based PR and release workflow](gitflow-pr-workflow.md) for label semantics and merge rules.

## Documentation site

Weave docs are published as a MkDocs site configured by `mkdocs.yml`. The site uses MkDocs Material; its MIT license was verified from upstream on 2026-05-24 and is safe for project use.

Canonical product/domain vocabulary lives in [Canonical feature models and provider facades](canonical-feature-models.md). Mermaid source diagrams are first-class review artifacts in [Diagrams](diagrams/index.md); keep provider-neutral domain models separate from adapter-specific notes so client and Admin Console contracts do not drift into provider SDK details.

For documentation-only changes:

```sh
python3 -m pip install -r docs/requirements.txt
make docs-check
```

`make docs-check` runs the lightweight repository docs validator and a strict MkDocs build. `make docs-build` is available when you only need the strict MkDocs build after dependencies are installed. Keep existing docs linked from navigation or from handbook pages so product truth does not drift into orphaned Markdown.

## Release notes workflow

Release-affecting changes must choose exactly one release-notes label in the PR. Use the fixed page categories `Added`, `Changed`, `Fixed`, `Security`, `Accessibility`, `Migration/Operator Notes`, and `Known Issues` when drafting checked-in notes. Put provider setup, SecretRef, OpenTofu/bootstrap, backup/restore, support-bundle, readiness, audit, and policy/whitelist impacts under `Migration/Operator Notes`.

Generated release notes come from merged PR metadata and labels. The local generator writes review artifacts under `build/release-notes/**` by default; checked-in README or docs mutations are explicit update steps:

```sh
./gradlew generateReleaseNotes  # offline fixture artifact for deterministic review
GH_TOKEN=... python3 tools/release_notes_generate.py --repo masssi164/weave --since 2026-05-24T21:09:00Z --output build/release-notes/unreleased.md
python3 tools/release_notes_generate.py --input tools/fixtures/release_notes_prs.json --output tools/fixtures/release_notes_unreleased.expected.md --check
python3 tools/readme_release_notes.py --update --source build/release-notes/unreleased.md
```

The `Release draft` GitHub Actions workflow is manual (`workflow_dispatch`) and creates or updates a **draft** release only. It generates notes from the same label policy, injects a README review artifact, uploads both artifacts, and never publishes automatically.

Run `./gradlew releaseEvidenceCheck` before requesting review when release notes are relevant; it validates release-note page structure, README release markers, label edge cases, and the generator fixture.

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

Live-stack commands require a prepared `infra/weave-workspace` stack and real test credentials. Do not commit credentials, generated secret files, screenshots containing secrets, or raw live logs with sensitive values.

## Quality and evidence artifacts

Use [Quality and acceptance evidence](quality-and-evidence.md) as the landing page for release evidence. It explains offline checks, screenshot drift, acceptance contract artifacts, live-stack uploads, and how to interpret failures without relying on stale workflow run IDs.

## Marketing screenshots

README images are deterministic SVGs generated by:

```sh
make marketing-screenshots
```

The generator is `tool/generate_marketing_screenshots.py`.

- Main product showcase assets live in `docs/assets/marketing/`.
- Calendar, boards/tasks, and LiveKit meetings guarded roadmap visuals live in `docs/assets/roadmap/`.
- Keep SVG `<title>` and `<desc>` meaningful.
- Keep README and docs `alt` text descriptive.
- Regenerate and review the SVG diff whenever copy, filenames, or visuals change.

## Documentation changes without drift

- Link existing docs instead of duplicating long contracts.
- Avoid absolute local filesystem paths.
- Use stable workflow and docs links, not one-off run IDs, for permanent evidence claims.
- Keep README claims tied to implemented or explicitly gated behavior.
- If docs change setup, contract behavior, public URLs, or live-stack expectations, check the matching `e2e/`, `server/`, or `infra/` contract before editing code.

## PR checklist

Before requesting review, include:

- Scope and user/developer impact.
- Contract impact, including whether specs changed.
- Accessibility and localization notes when UI-facing.
- Screenshot/docs impact when user-visible.
- Commands run and results.
- Any live-stack checks skipped and why.
