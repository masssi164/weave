## Summary

-

## Scope and user impact

-

## Spec / acceptance link

- Issue:
- Repo spec or spec note:
- Acceptance scenario / mapping marker when product behavior changed:
- Product-core questions intentionally left unresolved: none / listed below

## Branch, target lane, and release path

- Target lane: dev / future/* / rc/* / main exception
- [ ] Branch started from the correct lane (`dev`, `future/*`, `rc/*`, or `main` for hotfix/main exception)
- [ ] Linked issue(s) or explicit spec/evidence note are listed above
- [ ] `main` target is only an `rc/*` promotion or documented emergency `hotfix/*` exception
- [ ] Production release is not implied by this merge

## Spec impact

Choose one and explain when needed.

- [ ] none
- [ ] implements locked spec
- [ ] updates spec (linked corpus/spec task required):
- [ ] changes evidence only

## Release note

Use exactly one form.

- Release note:
- Release note: none — reason:

## Release notes label

Choose exactly one before review/merge; CI fails otherwise.

- [ ] `release-notes-feature`
- [ ] `release-notes-bugfix`
- [ ] `release-notes-skip`

## Screenshots or docs impact

-

## Accessibility and localization

-

## Contract impact

- [ ] No public API/auth/topology/spec contract change
- [ ] Contract/spec change documented:
- [ ] `./gradlew specContract` run for spec/product-contract changes

## Review readiness

- [ ] Copilot review requested for this review-ready PR, or Copilot exhaustion/unavailability noted
- [ ] Copilot findings addressed or fallback human/agent review evidence documented

## Checks run

- [ ] `git diff --check`
- [ ] `./gradlew specCorpusConformance`
- [ ] `./gradlew specContract`
- [ ] `./gradlew acceptanceContract`
- [ ] `./gradlew ci` (canonical cross-stack gate; attach or cite `build/evidence/ci-summary.json`)
- [ ] `make docs-check` / `make docs-build` (temporary Gradle-delegating aliases for docs or release notes changes)
- [ ] `make release-notes-check` (temporary Gradle-delegating alias for release-affecting changes)
- [ ] `python3 tools/pr_body_check.py <pr-body-file>` (when editing PR governance)
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
