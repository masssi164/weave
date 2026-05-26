## Summary

-

## Scope and user impact

-

## Branch, target, and release path

- [ ] Branch started from current `origin/main`
- [ ] PR targets protected `main`
- [ ] No long-lived `dev`/`testing`/`staging` branch involved
- [ ] Production release is not implied by this merge

## Screenshots or docs impact

-

## Accessibility and localization

-

## Contract impact

- [ ] No public API/auth/topology/spec contract change
- [ ] Contract/spec change documented:

## Release notes label

Choose exactly one before review/merge; CI fails otherwise.

- [ ] `release-notes-feature`
- [ ] `release-notes-bugfix`
- [ ] `release-notes-skip`

## Review readiness

- [ ] Copilot review requested for this review-ready PR
- [ ] Copilot findings addressed or fallback review evidence documented

## Checks run

- [ ] `./gradlew ci` (canonical cross-stack gate; attach or cite `build/evidence/ci-summary.json`)
- [ ] `make docs-check` / `make docs-build` (temporary Gradle-delegating aliases for docs or release notes changes)
- [ ] `make release-notes-check` (temporary Gradle-delegating alias for release-affecting changes)
- [ ] `flutter pub get`
- [ ] `flutter gen-l10n`
- [ ] `dart run build_runner build --delete-conflicting-outputs`
- [ ] `dart format --output=none --set-exit-if-changed .`
- [ ] `flutter analyze --fatal-infos`
- [ ] `flutter test`
- [ ] `make offline-contract-test`
- [ ] `make marketing-screenshots` (if README/docs screenshot assets changed)
- [ ] Live-stack validation (only when relevant; record run, artifact, or skip reason):

## Notes for reviewers

-
