# Quality and acceptance evidence

Weave uses layered evidence so contributors can move quickly while release claims stay tied to proof. This page explains what each layer proves, where artifacts are produced, and where to look when something fails.

## Evidence layers

| Layer | What it proves | Where to find it |
| --- | --- | --- |
| Offline Flutter checks | Formatting, static analysis, unit/widget behavior, generated code consistency, and no-network contract checks. | `.github/workflows/ci.yml`, local `flutter` commands, `make offline-contract-test`. |
| Accessibility gate | Automated checks and the manual assistive-technology evidence required before release sign-off. | [Accessibility Release Gate](accessibility-release-gate.md). |
| ISO 9241-110 dogfood UX gate | Product dialogue quality, release-scope capability states, banned preview/scaffold wording, and role separation for member vs admin/operator surfaces. | [ISO 9241-110 Dogfood UX Gate](iso-9241-110-dogfood-ux-gate.md), `test/release_1/ux_release_copy_contract_test.dart`. |
| Deterministic screenshots | README and roadmap SVG assets match the checked-in generator and do not drift silently. | `make marketing-screenshots`, `docs/assets/marketing/`, `docs/assets/roadmap/`, CI screenshot drift step. |
| Acceptance contract guard | Gherkin acceptance scenarios stay mapped to executable frontend/live-stack tests. | [Acceptance contracts](acceptance-contracts.md), [Product acceptance flows](product-acceptance-flows.md), `test/live_stack_feature_mapping_test.dart`. |
| Admin-provisioned first-use guard | Normal members stay out of OIDC/provider/infra setup and Workspace Health remains the admin/operator control plane. | [Admin-provisioned first use boundary](admin-provisioned-first-use.md), `client/test/architecture/admin_provisioned_first_use_contract_test.dart`, `client/test/features/settings/settings_screen_test.dart`, `client/test/features/onboarding/first_run_screen_test.dart`. |
| Docs build | User/admin handbook content builds without broken links, secret-pattern docs drift, or image-only instructions. | `build/docs/user` and `build/docs/admin` from `./gradlew docsBuild`. |
| CI summary artifact | The root Gradle task graph emitted a sanitized summary of commit, branch, tool versions, gate outcomes, artifact paths, and live-E2E skip reason. | `build/evidence/ci-summary.json` from `./gradlew ci` or `./gradlew ciSummary`. |
| Enterprise release gate contract | Release lanes, required gates, Live Stack artifact names, marker requirements, and waiver rules stay machine-checkable. | [Enterprise release foundation](enterprise-release-foundation.md), `release/enterprise-release-gates.json`, `./gradlew enterpriseReleaseGateCheck`. |
| Live Stack E2E | A prepared self-hosted stack can boot the app-level journey and upload acceptance evidence artifacts. | `.github/workflows/live-stack-e2e.yml` workflow runs and their uploaded artifacts. |

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

For README or screenshot changes, also run:

```sh
make marketing-screenshots
git diff --exit-code -- docs/assets/marketing docs/assets/roadmap
```

The canonical cross-stack command is:

```sh
./gradlew ci
```

It writes `build/evidence/ci-summary.json` even when the Gradle build fails, so reviewers can inspect a sanitized task-graph summary without reconstructing evidence from chat or raw logs. Use `./gradlew ciSummary` when you only need to regenerate the summary shape for review.

These checks are intentionally cheap enough for normal pull requests and do not require live credentials.

## Live-stack evidence

The live-stack path is expensive and runs on a dedicated self-hosted macOS ARM64 runner. Use it when a change affects sign-in, backend facade contracts, Matrix/files/calendar live behavior, acceptance scenarios, or integration boundaries.

The workflow prepares an acceptance evidence directory, runs the app-level live-stack E2E, and uploads support-safe acceptance evidence from the run. The artifact set includes `release-evidence-manifest.json`, which names the source lane, commit, workflow run metadata, artifact list, and RC promotion rule. On failure, the same uploaded artifact may include `failure-diagnostics/` with `failure-summary.md`, `failure-summary.json`, `container-status.tsv`, `failed-markers.json`, redacted readiness output, and a redacted support-bundle reference. It must not include blindly dumped raw container logs. Do not cite a single workflow run ID as a permanent product claim; link to the workflow, the relevant docs, the manifest, and the PR evidence instead.

## Interpreting pass/fail states

- **Offline Flutter failure**: inspect the failing command first. Re-run locally after regenerating l10n/build output.
- **Screenshot drift**: run `make marketing-screenshots`, review the SVG diff as product copy, and commit the regenerated assets if intentional.
- **Acceptance mapping failure**: update the scenario-to-test mapping or remove stale scenario claims. Do not leave product acceptance text unmapped.
- **Live-stack contract failure**: start with `failure-diagnostics/failure-summary.md`, `container-status.tsv`, `health-checks/operator-check.txt`, and `failed-markers.json` in the uploaded evidence artifact. Treat credential, runner, or environment problems as named infrastructure blockers, not product proof. Missing required markers such as `BOARDS_RESULT` block RC promotion until a green rerun or explicit release-owner waiver exists. Operators who need deeper private debugging may rerun diagnostics on the self-hosted runner with private raw-log collection enabled, but those files stay outside uploaded/support evidence.
- **Accessibility evidence gap**: do not promote the flow as release-ready until the automated and manual evidence in the accessibility gate is complete.
- **Admin-provisioned first-use failure**: inspect member-visible first-use, settings, and navigation copy first. Normal members must not see provider setup diagnostics, OIDC/provider/infra setup fields, preview/scaffold/coming-soon release-scope language, or raw provider errors; move setup/readiness detail to Workspace Health for admins/operators.

## Artifact hygiene

- Never commit live credentials, generated secret files, or raw logs that contain tokens/passwords.
- Live Stack failure artifacts must use support-safe diagnostics by default: status, readiness, failed markers, and redacted support bundles instead of raw provider/container logs.
- Prefer redacted summaries in docs and PR bodies.
- Use `build/evidence/ci-summary.json` for task outcomes; it must not include secrets, tokens, credential URLs, raw provider payloads, or raw provider errors.
- Keep evidence explanations screen-reader friendly; do not make images or badge colors the only source of truth.
- For permanent docs, link to workflow files and evidence procedures rather than transient artifact URLs.

## Related docs

- [Developer handbook](developer-handbook.md)
- [Admin-provisioned first use boundary](admin-provisioned-first-use.md)
- [Acceptance contracts](acceptance-contracts.md)
- [Product acceptance flows](product-acceptance-flows.md)
- [Accessibility Release Gate](accessibility-release-gate.md)
- [ISO 9241-110 Dogfood UX Gate](iso-9241-110-dogfood-ux-gate.md)
- [Roadmap and guarded surfaces](roadmap-and-guarded-surfaces.md)
- [Enterprise release foundation](enterprise-release-foundation.md)
