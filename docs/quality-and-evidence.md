# Quality and acceptance evidence

Weave uses layered evidence so contributors can move quickly while release claims stay tied to proof. This page explains what each layer proves, where artifacts are produced, and where to look when something fails.

## Evidence layers

| Layer | What it proves | Where to find it |
| --- | --- | --- |
| Offline Flutter checks | Formatting, static analysis, unit/widget behavior, generated code consistency, and no-network contract checks. | `.github/workflows/ci.yml`, local `flutter` commands, `make offline-contract-test`. |
| Accessibility gate | Automated checks and the manual assistive-technology evidence required before release sign-off. | [Accessibility Release Gate](accessibility-release-gate.md). |
| ISO 9241-110 dogfood UX gate | Product dialogue quality, release-scope capability states, banned preview/scaffold wording, and role separation for member vs admin/operator surfaces. | [ISO 9241-110 Dogfood UX Gate](iso-9241-110-dogfood-ux-gate.md), `test/release_1/ux_release_copy_contract_test.dart`. |
| Deterministic screenshots | README and roadmap SVG assets match the checked-in generator and do not drift silently. | `make marketing-screenshots`, `docs/assets/screenshot-evidence.json`, `docs/assets/marketing/`, `docs/assets/roadmap/`, CI screenshot drift step. |
| Spec contract guard | Repo-local specs keep required metadata, lifecycle status, evidence gates, and implementation-ready clarification discipline. | [Spec-driven development for Weave](spec-driven-development.md), `.specify/memory/constitution.md`, `specs/`, `./gradlew specContract`, `./gradlew specContractTest`. |
| Acceptance contract guard | Gherkin acceptance scenarios stay mapped to executable frontend/live-stack tests. | [Acceptance contracts](acceptance-contracts.md), [Product acceptance flows](product-acceptance-flows.md), `test/live_stack_feature_mapping_test.dart`. |
| Admin-provisioned first-use guard | Normal members stay out of OIDC/provider/infra setup and Workspace Health remains the admin/operator control plane; only the current app-start contract can enter the authenticated shell, and obsolete local setup state is rejected rather than upgraded. | [Admin-provisioned first use boundary](admin-provisioned-first-use.md), `client/test/architecture/admin_provisioned_first_use_contract_test.dart`, `client/test/architecture/obsolete_member_entry_contract_test.dart`, `client/test/core/router/app_router_test.dart`, and `client/test/features/settings/settings_screen_test.dart`. |
| Docs build | User/admin handbook content builds without broken links, secret-pattern docs drift, or image-only instructions. | `build/docs/user` and `build/docs/admin` from `./gradlew docsBuild`. |
| CI summary artifact | The root Gradle task graph emitted a sanitized summary of commit, branch, tool versions, gate outcomes, artifact paths, and live-E2E skip reason. | `build/evidence/ci-summary.json` from `./gradlew ci` or `./gradlew ciSummary`. |
| Enterprise release gate contract | Release lanes, required gates, Live Stack artifact names, marker requirements, and waiver rules stay machine-checkable. | [Enterprise release foundation](enterprise-release-foundation.md), `release/enterprise-release-gates.json`, `./gradlew enterpriseReleaseGateCheck`. |
| Beta readiness claim gates | Sprint 32 Beta claims stay scoped to the end-to-end Admin, User, governed Weaver, and foundation slice, with each claim mapped to CI, E2E, migration dry-run, accessibility smoke, and release-note evidence before promotion. | [Beta readiness slice and claim gates](beta-readiness-claim-gates.md), GitHub issues #830-#836, and the owning issue gates. |
| Fresh product flow | A disposable stack proves invitation, Keycloak browser activation, PKCE, WebDAV, workload OAuth, MCP, revocation, and cleanup without credential-injected Flutter builds. | `./gradlew testApp`, `.github/workflows/live-stack-e2e.yml`, and `weave-test-app-evidence.json`. |
| Physical client auth | The production Flutter AppAuth integration opens the system browser, restores the workspace, and refreshes the session on a physical device. | `make physical-device-auth-e2e` and protected physical-device evidence. |

## Default PR validation

For most Flutter-only changes, run:

```sh
flutter pub get
flutter gen-l10n
dart run build_runner build --delete-conflicting-outputs
dart format --output=none --set-exit-if-changed .
flutter analyze --fatal-infos
flutter test
make offline-contract-test
```

For spec or product-contract changes, also run:

```sh
./gradlew specContract
./gradlew specContractTest
```

For README or screenshot changes, also run:

```sh
make marketing-screenshots
python3 tools/screenshot_evidence_check.py
git diff --exit-code -- docs/assets/marketing docs/assets/roadmap
```

The canonical cross-stack command is:

```sh
./gradlew ci
```

It writes `build/evidence/ci-summary.json` even when the Gradle build fails, so reviewers can inspect a sanitized task-graph summary without reconstructing evidence from chat or raw logs. Use `./gradlew ciSummary` when you only need to regenerate the summary shape for review.

These checks are intentionally cheap enough for normal pull requests and do
not require human credentials.

## Live-stack and persistent test-stack evidence

The live-stack path is expensive and runs on a dedicated self-hosted macOS ARM64 runner. Use it when a change affects sign-in, backend facade contracts, Matrix/files/calendar live behavior, acceptance scenarios, or integration boundaries.

There are five deliberately separated lanes:

- `Live Stack Product Flow` (`.github/workflows/live-stack-e2e.yml`) runs
  `./gradlew testApp`. The bounded Java process creates owner/collaborator/outsider invitations,
  reads one-time activation links from Mailpit, completes Keycloak required
  actions in Chromium, performs fresh Authorization Code with PKCE sessions,
  repeats real cross-identity Chat, Files, Calendar, Home, and Profile behavior
  twice, proves canonical JPA/PostgreSQL and direct Synapse state plus restart,
  outage/retry, callback-replay and outsider-denial behavior, proves workload-only
  MCP `files.search` revoke/regrant, writes allowlisted support-safe evidence, and
  tears down only its namespace.
- `iOS Dogfood` runs the current member UI surfaces on a newly created iPhone
  Simulator before archive or physical installation. Its repositories are
  fixtures and its artifact is labelled `fixture-ui`; only the validator may
  combine it with the exact live artifact. It is not authentication, provider,
  multi-user, VoiceOver, or physical-device evidence.
- `physical-device-auth-e2e` is interactive and never runs on a simulator. It
  uses the production Flutter `flutter_appauth` client and accepts only public
  endpoints/client ID as build arguments. The human enters credentials directly
  in Keycloak's system-browser surface.
- `Physical iPhone Human Test` validates a tester-supplied, support-safe twenty-step protocol
  against the exact manifest-bound deployment and iOS distribution. `Human Testing Readiness`
  consumes that artifact and cannot manufacture human outcomes, timestamps, or confirmation. Its
  final persistent-runner step independently reverifies all four running image identities and
  captures a new cached provider-health observation; the older deployment snapshot is historical
  evidence only and cannot satisfy the final freshness gate.
- `Test Stack Deploy` (`.github/workflows/test-stack-deploy.yml`) is the persistent LAN dogfood stack for the `dogfood` branch. It starts only from successful exact-candidate isolated evidence, applies the candidate twice, runs the non-destructive operator checks without the automation-user smoke suite, proves Compose and runtime idempotency plus persistent human/Mailpit/TLS/session invariants, and leaves the verified stack running for human testing.

The persistent test stack is the required bridge between `dev` and `main`: a commit may be promoted to `main` only after it is contained in `dev`, contained in `dogfood`, and has a successful `Test Stack Deploy` run on `dogfood`. See [Dev/Dogfood/Main promotion flow](dev-test-main-promotion-flow.md).

The disposable workflow uploads the product result, exact teardown result, immutable
candidate manifest/mapping, and a derived live-only automation summary. Each binds
the exact implementation commit, pinned specification commit, and isolated
Compose project and contains timestamps, hashes, protocol/result enums, the MCP
tool/projection, and explicit `credentialsIncluded=false`,
`actionLinksIncluded=false`, and `supportSafe=true` flags. Passwords, activation
URLs, bearer tokens, client assertions, private keys, emails, raw client IDs,
provider payloads, and raw logs remain outside durable evidence.

The support-safe Synapse compatibility probe is separate from the full Live Stack. Run `python3 tools/synapse_compatibility_probe.py --target <supported-version> --output <private-build-path>` against each versioned target. It creates and removes one uniquely named Synapse container, injects one Application Service outage, and records only version/profile booleans plus a signature hash. A green probe does not authorize a Synapse pin change or substitute for exact-candidate collaboration, cleanup, teardown, physical-device, or distribution evidence.

The self-hosted runner serializes product-flow, persistent dogfood, recovery, and
physical distribution work. `testApp` uses a run-unique namespace for
containers, volumes, network, ports, state, people, and workload clients. A
persistent-dogfood guard captures owned resource identities before the run and
verifies them unchanged afterwards. No Simulator, Flutter VM-service scraping,
test-only certificate callback, custom OIDC HTTP driver, or credential-bearing
Dart define belongs to this lane.

## Interpreting pass/fail states

- **Offline Flutter failure**: inspect the failing command first. Re-run locally after regenerating l10n/build output.
- **Screenshot drift**: run `make marketing-screenshots`, validate `docs/assets/screenshot-evidence.json`, review the SVG diff as product copy, and commit regenerated assets if intentional.
- **Spec contract failure**: fix missing frontmatter/lifecycle metadata, resolve implementation-ready `[NEEDS CLARIFICATION: ...]` markers, or move the spec back to `draft`/`proposed` until the product-core question is answered.
- **Acceptance mapping failure**: update the scenario-to-test mapping or remove stale scenario claims. Do not leave product acceptance text unmapped.
- **Product-flow failure**: inspect the `testApp` Gradle/JUnit result and private
  namespace logs on the runner. The uploaded support-safe evidence must never be
  expanded into a raw log bundle. A missing or invalid
  `weave-test-app-evidence.json` blocks promotion.
- **Accessibility evidence gap**: do not promote the flow as release-ready until the automated and manual evidence in the accessibility gate is complete.
- **Admin-provisioned first-use failure**: inspect member-visible first-use, settings, and navigation copy first. Normal members must not see provider setup diagnostics, OIDC/provider/infra setup fields, preview/scaffold/coming-soon release-scope language, or raw provider errors; move setup/readiness detail to Workspace Health for admins/operators.

## Artifact hygiene

- Never commit human credentials, generated secret files, activation links, or
  raw logs that contain tokens/passwords.
- Live Stack failure artifacts must use support-safe diagnostics by default: status, readiness, failed markers, and redacted support bundles instead of raw provider/container logs.
- Prefer redacted summaries in docs and PR bodies.
- Use `build/evidence/ci-summary.json` for task outcomes; it must not include secrets, tokens, credential URLs, raw provider payloads, or raw provider errors.
- Keep evidence explanations screen-reader friendly; do not make images or badge colors the only source of truth.
- For permanent docs, link to workflow files and evidence procedures rather than transient artifact URLs.

## Related docs

- [Developer handbook](developer-handbook.md)
- [Admin-provisioned first use boundary](admin-provisioned-first-use.md)
- [Spec-driven development for Weave](spec-driven-development.md)
- [Acceptance contracts](acceptance-contracts.md)
- [Product acceptance flows](product-acceptance-flows.md)
- [Beta readiness slice and claim gates](beta-readiness-claim-gates.md)
- [Accessibility Release Gate](accessibility-release-gate.md)
- [ISO 9241-110 Dogfood UX Gate](iso-9241-110-dogfood-ux-gate.md)
- [Roadmap and guarded surfaces](roadmap-and-guarded-surfaces.md)
- [Enterprise release foundation](enterprise-release-foundation.md)
