# Developer handbook

This handbook is the practical entry point for contributing to the Weave monorepo, especially the Flutter client. It complements the repo README and links to deeper docs instead of repeating every contract.

For #1470 delivery, apply the pinned corpus release profile at `steering/release-2026-10-product-consolidation.md` before older Core workflow or gateway instructions. Delivery follows pinned `steering/devops-conformance.md` and ADR 0022. Ordinary Dev PRs run deterministic checks. The protected Dogfood candidate consumes verified hosted artifacts and runs mandatory Compose plus two concurrent independent iOS Simulator Flutter apps using two ordinary members of the prepared organization and shared Space. macOS desktop testing is manual-only. Deployment and in-place physical startup preparation precede separate human acceptance. Main verifies existing exact-candidate evidence rather than repeating equivalent integration. Server code generates separate User and Admin API artifacts; product HTTP consumers use generated clients and transport models. Chat uses Weave's Matrix Client-Server facade, the narrowly scoped Rust/Ruma/JNI wire boundary, canonical Chat persistence, native Flutter and Weaver Matrix integrations, and `ChatProviderPort` routing. Flutter uses the normal member OIDC/PKCE session at that facade; independent third-party Matrix clients and their separate OAuth profile are deferred. E2EE is a per-room policy whose cryptographic state stays client-owned when enabled. Public northbound DAV is outside this release. Run independent authorization and real Weave-owned Matrix/user/admin/MCP journeys in their applicable suites in addition to generation freshness. Provider adoption, migration, permission equivalence, cutover, and rollback are #1498 evidence.

## Repository role

Weave is now a monorepo product stack:

| Directory | Responsibility |
| --- | --- |
| `client/` | Flutter mobile/desktop app shell, custom chat/files/settings UX, client validation, accessibility, deterministic screenshots. |
| `server/` | Product API/BFF, auth/profile facade, files/calendar/boards facades, authorization, audit, readiness, and backend contract tests. |
| `weave-application-core/`, `weave-files-core/` | Framework-free canonical values, use cases, and ports. |
| `weave-persistence-jpa/`, `weave-runtime-*-adapters/` | Portable entity persistence and explicitly composed provider/security adapters. |
| `weave-mcp-server/` | Separate workload-only Spring AI MCP process using the generated Weave User API client for product HTTP calls. |
| `weave-product-e2e/` | Plain-Java real-browser/OIDC/User API/MCP product proof; no runtime beans or implementation-module dependency. |
| `infra/` | Local/dev stack, Caddy, Keycloak, optional providers, Docker Compose orchestration, live-stack smoke and E2E environment. |
| `e2e/` | Binding product-language Gherkin scenarios, scenario mapping, and sanitized evidence contract. |
| `release/` | Stack manifests and release compatibility metadata. |

Do not invent backend endpoints, public URLs, auth claims, or infra defaults in Flutter. If a change needs a contract change, update `server/`, `infra/`, `e2e/`, and the relevant docs in the same monorepo change.

## Local setup

Prerequisites:

- Flutter SDK on the stable channel.
- Xcode/macOS or another Flutter-supported target for local app runs.
- `make`, Python 3, Java 21+ for Gradle/backend checks, and the normal Dart/Flutter toolchain.
- Node/npm for admin console checks.
- Docker with Compose for dev, dogfood, and live-stack validation.

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
dart run build_runner build --force-aot --delete-conflicting-outputs
flutter run
```

Members enter only the public Weave organization origin, such as `weave.test`, which normalizes to HTTPS. The generated User `GET /api/platform/config` operation delivers the canonical OrgManifest v2 at that product origin. Its validated contract supplies a credential-free Weave Matrix Client-Server URL, distinct from the User API URL; it must not advertise a concrete southbound Chat provider. The Weave-owned Flutter Matrix path uses the normal member OIDC/PKCE session at that facade. Live release confidence still requires exact-candidate native and integrated evidence; generation and widget checks alone do not qualify a release. Member Files and Calendar use the generated Weave User API; Nextcloud WebDAV/OCS and CalDAV remain southbound provider-adapter mechanisms.

Android debug/profile runs use the production package identity `com.massimotter.weave` and the same OIDC redirect scheme. Keep identity-provider clients configured for `com.massimotter.weave:/oauthredirect` and `com.massimotter.weave:/logout`. Release artifact tasks require a local untracked `client/android/key.properties` file and keystore; they fail closed instead of using debug signing keys.


## Root Gradle orchestration

The root `./gradlew` is the monorepo build/delivery source of truth. GitHub Actions installs pinned ecosystem dependencies and runs `./gradlew ci`; Make targets are temporary compatibility aliases that delegate to Gradle during the transition. Do not add new root Make-only build logic.

| Gradle task | Purpose |
| --- | --- |
| `dogfoodPrepare` | Explicit reviewed local-trust and approved test-member preparation; preserves owner state and does not replace qualification, physical startup observation or human acceptance. |
| `doctor` | Checks required tools and pinned dependency files with actionable failures. |
| `acceptanceContract` | Gherkin mapping and acceptance contract guard. |
| `clientCi` | Flutter generated-code, format, analysis, tests, screenshot drift, and offline contract path. |
| `serverCi` | Existing server Gradle test path. |
| `generatedApiContractCi` | Code-first User/Admin export and freshness, before consumer impact selection. |
| `generatedUserApiConsumerCi`, `generatedAdminApiConsumerCi` | Independently selected consumer regeneration and verification using complete dependency inputs. |
| `generatedApiCi` | Compatibility aggregate for contract and all consumer verification. |
| `mcpCi` | Separate MCP process, security, schema, and architecture checks. |
| `testAppContract` | Compile/unit/ArchUnit checks for the framework-free Fresh product-flow driver. |
| `testApp` | Protected Dogfood selects `WEAVE_TEST_APP_MODE=member-collaboration`: separate bounded setup, two prepared ordinary members, real concurrent dual-iOS UI collaboration, authorization probes and exact cleanup, using hosted runtime/native artifacts and a precompiled JVM driver. The separately selectable `full` mode retains invitation/activation, admin and MCP journeys; it remains the manual default. macOS desktop testing is manual-only. Provider migration is separate. |
| `adminCi` | Admin console npm CI path. |
| `infraStatic` | Compose, renderer, lifecycle, secret, and infrastructure contract checks. |
| `docsBuild` | Strict MkDocs build with deterministic outputs under `build/docs/user` and `build/docs/admin`. |
| `docsCheck` | Docs structure check plus strict MkDocs build. |
| `releaseNotesLabelCheck` | Current PR release-notes label validation when `PR_LABELS_JSON` is available; skipped locally when unset. |
| `androidReleaseIdentityCheck` | Android package identity, OIDC redirect alignment, and release-signing fail-closed guard. |
| `releaseEvidenceCheck` | Current release claim, privacy/accessibility/recovery guards, release-note generation and exact-candidate automated/physical/human evidence regressions. Historical enterprise and RC fixtures remain standalone checks. |
| `releaseReadinessCheck` | Validates support-safe RC readiness evidence for an explicit candidate without publishing a release; override with `-PcandidateVersion`, `-PcandidateTag`, `-PcandidateCommit`, and evidence path properties. |
| `ci` | Canonical aggregate for the PR-safe monorepo gate set. |

Each task requires the same tools and dependency setup as the underlying ecosystem command; for example docs tasks need pinned dependencies installed in `build/docs-venv` or the active Python environment from `docs/requirements.txt`, server checks need Java 21+, client checks need Flutter/Dart, and admin checks need Node/npm dependencies. `./gradlew ci` writes sanitized evidence to `build/evidence/ci-summary.json`; CI uploads `build/evidence/**` and deterministic docs outputs as artifacts. If local `./gradlew doctor` reports JDK 17, point `JAVA_HOME` at JDK 21+ rather than weakening the gate.

## Focused Server domain checks

For focused Server feedback, use
`./gradlew :server:domains:check -Pdomains=files,chat`.
The selected-domain JVM lane defaults to `all` after any matching branch pin;
it does not replace the complete Server/PostgreSQL/JNI gates or make production
packaging domain-selectable. See [Selected domain JVM verification](development/domain-verification.md)
for selection rules, CI dispatch and the current bounded test inventory.

## Everyday Flutter workflow

Use the existing generated-code workflow before analysis or tests:

```sh
flutter pub get
flutter gen-l10n
dart run build_runner build --force-aot --delete-conflicting-outputs
```

Then run the cheap PR-safe gate:

```sh
dart format --output=none --set-exit-if-changed .
flutter analyze --fatal-infos
flutter test
make offline-contract-test
```

`make offline-contract-test` runs the no-network contract path with endpoint
metadata only. Credential build arguments are not part of the client test
contract.

## Generated code and l10n

- Do not hand-edit generated Dart output.
- Use build_runner's public `--force-aot` path for pure generation. It preserves builder discovery and freshness while avoiding a recursive native-assets build. Flutter and native tests retain their normal hooks and runtime checks.
- Run `flutter gen-l10n` after localization changes.
- Run `dart run build_runner build --force-aot --delete-conflicting-outputs` after model/provider/codegen changes.
- Keep user-facing strings localizable; do not add hard-coded English text in widgets when the surrounding feature already uses l10n.

## Lane-based PR and release workflow

Use the lane-based DevOps flow: protected `dev` for integration and normal feature PRs, protected `dogfood` for exact automated integration followed by resettable LAN Compose and distinct human validation, and protected `main` for qualified stable source. Production publication requires its separate accepted launch contract and authorization. `future/*`, optional `rc/*`, and `hotfix/*` remain available when those release lanes are actually needed. See [Weave DevOps branching model](devops/branching-model.md), [release flow](devops/release-flow.md), [release notes policy](devops/release-notes.md), and [spec governance](devops/spec-governance.md). Every PR must deliberately choose exactly one release-notes label before review/merge:

- `release-notes-feature`
- `release-notes-bugfix`
- `release-notes-skip`

Release notes are generated from merged PR labels, not manually reconstructed later. The CI `Release Notes Label Check` runs on every pull-request update and fails PRs with zero or multiple release-notes labels; label-only changes run that lightweight check without re-running the full foundation chain. The protected `Gradle CI` context comes only from a full exact-head run; the skipped label-only aggregate has a distinct name. See [Weave operating model](weave-operating-model.md) for the delivery contract and [Lane-based PR and release workflow](gitflow-pr-workflow.md) for label semantics and merge rules.

## Spec-driven sprint contract

A Weave sprint is not one green PR. A sprint starts from an accepted or explicitly proposed specification and ends only after the planned issue/PR train has been integrated or a product-core blocker is recorded.

Required sprint shape:

Sprint bootstrap and audit are file-backed by `tools/sprint_bootstrap.py`; see `docs/sprint-bootstrap-and-board-flow.md`. This is the bridge from the pinned spec corpus to GitHub milestones, issues, the Weave Delivery Board, and CI/evidence gates.

1. Recover fachliche truth from the pinned spec corpus first, then implementation truth from current `dev` and `main`, transitional `specs/`, GitHub issues/PRs, and CI artifacts.
2. Select or create the governing spec-corpus contract; keep product-core ambiguity in `draft`/`proposed` with explicit `[NEEDS CLARIFICATION: ...]` markers.
3. Break the spec plan/tasks into a GitHub issue DAG with `parallel`/`sequential` labels where ordering matters.
4. For each implementation slice, open a short-lived branch and one reviewable PR with exactly one release-notes label.
5. Merge PRs in dependency order after green CI, required local gates, and fallback human/agent review evidence. Copilot review is optional while premium review requests are exhausted.
6. After each Dev merge, update local `dev` before cutting the next dependent branch. Qualify and human-test the exact Dogfood candidate before Main promotion.
7. Finish with a sprint closure report that lists the spec, issue DAG, merged PRs in order, gates/CI evidence, unresolved decisions, release/RC follow-up, and next safe action.

The `weave-co-leader` is expected to drive this loop autonomously when given a sprint goal. It should ask Massimo only for product-core decisions, destructive/live-infra approval, or merge/release decisions not already authorized by the sprint brief.

## Documentation site

Weave docs are published as a MkDocs site configured by `mkdocs.yml`. The site uses MkDocs Material; its MIT license was verified from upstream on 2026-05-24 and is safe for project use.

Canonical product/domain vocabulary lives in [Canonical feature models and provider facades](canonical-feature-models.md). Mermaid source diagrams are first-class review artifacts in [Diagrams](diagrams/index.md); keep provider-neutral domain models separate from adapter-specific notes so client and Admin Console contracts do not drift into provider SDK details.

For documentation-only changes:

```sh
python3 -m pip install -r docs/requirements.txt
make docs-check
```

`make docs-check` runs the lightweight repository docs validator and a strict MkDocs build. `make docs-build` is available when you only need the strict MkDocs build after dependencies are installed. Keep existing docs linked from navigation or from handbook pages so implementation-facing product guidance does not drift into orphaned Markdown.

## Release notes workflow

Release-affecting changes must choose exactly one release-notes label in the PR. Use the fixed page categories `Added`, `Changed`, `Fixed`, `Security`, `Accessibility`, `Migration/Operator Notes`, and `Known Issues` when drafting checked-in notes. Put provider setup, SecretRef, Compose/bootstrap, backup/restore, support-bundle, readiness, audit, and policy/whitelist impacts under `Migration/Operator Notes`.

Generated release notes come from merged PR metadata and labels. The local generator writes review artifacts under `build/release-notes/**` by default; checked-in README or docs mutations are explicit update steps:

```sh
./gradlew generateReleaseNotes  # offline fixture artifact for deterministic review
GH_TOKEN=... python3 tools/release_notes_generate.py --repo masssi164/weave --since 2026-05-24T21:09:00Z --output build/release-notes/unreleased.md
python3 tools/release_notes_generate.py --input tools/fixtures/release_notes_prs.json --output tools/fixtures/release_notes_unreleased.expected.md --check
python3 tools/readme_release_notes.py --update --source build/release-notes/unreleased.md
```

The `Release draft` GitHub Actions workflow is manual (`workflow_dispatch`) and creates or updates a **draft** release only. It generates notes from the same label policy, injects a README review artifact, uploads both artifacts, and never publishes automatically.

Run `./gradlew releaseEvidenceCheck` before requesting review when release notes are relevant; it validates release-note page structure, README release markers, label edge cases, the generator fixture, enterprise release gates, and RC readiness fixtures. For an actual candidate, run `./gradlew releaseReadinessCheck` or `python3 tools/release_readiness_check.py` with exact candidate commit and downloaded evidence artifacts.

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
| Client contract | `make integration-test` | PR-safe endpoint, generated-client, mapping, and release-spine validation without credentials. |
| Product E2E | `./gradlew testApp` | Disposable invitation, browser activation, PKCE, backend, MCP, provider, revocation, and cleanup proof. |
| Physical auth | `make physical-device-auth-e2e` | Interactive production AppAuth/system-browser flow on a connected physical device. |
| CI product flow | `.github/workflows/live-stack-e2e.yml` | Self-hosted `testApp` evidence with support-safe uploaded artifacts. |

The physical lane accepts only public endpoints and a client ID as build
arguments. Human credentials are entered into Keycloak in the system browser;
do not pass them through Flutter defines or evidence.

## Quality and evidence artifacts

Use [Quality and acceptance evidence](quality-and-evidence.md) as the landing page for release evidence. It explains offline checks, screenshot drift, acceptance contract artifacts, live-stack uploads, and how to interpret failures without relying on stale workflow run IDs.

## Marketing screenshots

README images are deterministic SVGs generated by:

```sh
make marketing-screenshots
```

The generator is `client/tool/generate_marketing_screenshots.py`.

- Main product showcase assets live in `docs/assets/marketing/`.
- Calendar, boards/tasks, and MatrixRTC Calls guarded roadmap visuals live in `docs/assets/roadmap/`.
- The evidence manifest lives at `docs/assets/screenshot-evidence.json`.
- Keep SVG `<title>` and `<desc>` meaningful.
- Keep README and docs `alt` text descriptive.
- Regenerate, validate the manifest, and review the SVG diff whenever copy, filenames, or visuals change.

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
