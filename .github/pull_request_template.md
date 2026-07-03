## Summary

-

## Scope and user impact

-

## Spec / acceptance link

- Issue:
- Repo spec / Spec Kit package:
- Plan/tasks/traceability:
- Mapped Gherkin feature/scenario when product behavior changed:
- Evidence mode / reality level: `offline-spec` or `live-runtime`; `contract_only` / `configured` / `live_read` / `live_write` / `migration_dry_run` / `migration_apply_ready` / `rollback_ready` / `release_ready` when applicable
- Product-core questions intentionally left unresolved: none / listed below

Quality Reset rule: product behavior and product claims require repo-local specs plus mapped Gherkin acceptance before merge. `offline-spec` / `contract_only` fixture evidence must not be described as `live-runtime`, isolated E2E, `release_ready`, or customer-ready evidence.

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

## Risk, privacy, accessibility, and localization

- Security/privacy impact:
- Accessibility/localization impact:
- Support-safe evidence constraints checked: no secrets, raw bearer tokens, raw provider payloads, raw endpoints, `openclaw.json`, or SecretRef/CredentialRef values.

## Contract impact

- [ ] No public API/auth/topology/spec contract change
- [ ] Contract/spec change documented:
- [ ] `./gradlew specContract` run for spec/product-contract changes

## Review readiness and Fachveto

- [ ] Copilot review requested for this review-ready PR, or Copilot exhaustion/unavailability noted
- [ ] Copilot findings addressed or fallback human/agent review evidence documented
- [ ] Relevant Fachveto owner/path named below; small pure bugfixes may use a lightweight veto path
- Fachveto owner/path:
- If Copilot is unavailable/insufficient, matching reviewer used:

## Checks run

- [ ] `git diff --check`
- [ ] `./gradlew specCorpusConformance` when product/domain specs or projections changed
- [ ] `./gradlew specContract`
- [ ] `./gradlew acceptanceContract`
- [ ] `python3 tools/e2e_structure_check.py` when Gherkin/scenario mappings changed
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
- [ ] `python3 tools/screenshot_evidence_check.py` (if README/docs screenshot assets changed)
- [ ] Live-stack validation (only when relevant; record run, artifact, or skip reason):

## Notes for reviewers

- Review for claim hygiene first: spec/Gherkin/evidenceMode/realityLevel must match the PR wording.
- Approval semantics must distinguish OpenClaw exec approval states from product user permissions; never treat `allow always` as blanket product permission.
- Keep Massimo's OpenClaw agent hierarchy, allowlists, model routing, personal operator paths, and runtime configuration out of product repo files.
