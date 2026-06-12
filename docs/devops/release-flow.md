# Weave release flow

This release flow keeps `main` stable while still giving Weave a practical integration lane for issues, specification work, and release-candidate evidence.

## Normal issue flow

- Recover truth from the pinned spec corpus in `specs/weave-specs.lock.json` and the linked issue.
- Create a short-lived issue branch from current `dev`.
- Implement the smallest reviewable slice.
- Update acceptance evidence, docs, and release-note text in the same PR when behavior or operations change.
- Open the PR to `dev` with the PR template filled.
- Run the smallest meaningful local gate and let CI validate the lane.

## Spec integration flow

- Treat the pinned Weave Specification Corpus as fachliche product/domain truth.
- Use this repo for implementation evidence, generated/transitional projections, and conformance checks.
- If implementation reality and corpus truth disagree, choose one explicit path:
  - open or link a spec-change task in the corpus; or
  - open or link a conformance-fix task in this repo.
- Do not let implementation state silently redefine product or domain meaning.

## Release-candidate flow

- Cut `rc/<version>` from `dev` only after the candidate scope is coherent.
- Freeze scope on `rc/<version>` to stabilization, evidence, release-note fixes, and release blockers.
- Run release evidence gates, including Live Stack E2E when relevant for the release.
- Record candidate evidence in the release evidence docs or artifacts.
- Promote `rc/<version>` to `main` by PR only when gates and reviews are green or an accepted release-gate exception is documented.
- Tag releases from `main` after promotion.

## Hotfix flow

- Cut `hotfix/*` from `main` only for urgent stable-line fixes.
- Keep the hotfix narrow and evidence-backed.
- PR to `main` with the `main` exception explained in the PR body.
- Immediately backport or merge the accepted fix into `dev` and any affected active `rc/*` lane.

## No release-ready claim by default

A green PR or local gate is not a release-ready claim. Release readiness requires the relevant `rc/*` evidence, release notes, protected checks, and closure of named release blockers.
