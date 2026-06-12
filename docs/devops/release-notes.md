# Release notes policy

Every issue and PR must make its release-note impact explicit. This keeps generated release notes traceable and prevents silent product, operator, or support changes.

## PR body requirement

Each PR includes one of these forms:

- `Release note: <short user/admin/operator/developer-facing note>`
- `Release note: none — <reason>`

Valid `none` reasons include:

- test-only maintenance with no behavior or support impact;
- internal refactor with no user, admin, operator, developer, or release-process effect;
- duplicate note already covered by a linked PR;
- evidence-only update with no change to shipped behavior.

Do not use `none` for docs, release-process, admin/operator behavior, infrastructure behavior, accessibility, localization, or supportability changes that a reader of release notes should know about.

## Label requirement

Each PR still carries exactly one release-notes label:

- `release-notes-feature`
- `release-notes-bugfix`
- `release-notes-skip`

The label controls generated release-note grouping. The PR-body release-note line records the human-readable note or the skip reason.

## Issue requirement

Issues should include a release-note expectation when acceptance is defined:

- expected release-note wording for user/admin/operator-facing changes;
- `Release note: none` with a reason for work that should not appear in notes;
- any linked spec or evidence artifact that constrains the wording.

## Release-candidate check

Before promoting an `rc/*` lane to `main`, verify that included PRs have:

- exactly one release-notes label;
- a release-note line or explicit none reason;
- no unresolved release-note TODOs;
- generated draft notes that match the release scope.
