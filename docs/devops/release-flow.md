# Weave release flow

This release flow keeps `main` stable while giving Weave a practical integration lane for issues, a persistent dogfood lane for candidate validation, and optional later release-hardening lanes.

## Normal issue flow

- Recover truth from the pinned spec corpus in `specs/weave-specs.lock.json` and the linked issue.
- Create a short-lived issue branch from current `dev`.
- Implement the smallest reviewable slice.
- Update feature-specific tests, acceptance/Gherkin/Cucumber scenarios or deterministic mappings, docs, evidence, and release-note text in the same PR when behavior or operations change.
- Open the PR back to `dev` with the PR template filled.
- Run the smallest meaningful local gate and let CI validate the lane.

## Spec integration flow

- Treat the pinned Weave Specification Corpus as fachliche product/domain truth.
- Use this repo for implementation evidence, generated/transitional projections, and conformance checks.
- If implementation reality and corpus truth disagree, choose one explicit path:
  - open or link a spec-change task in the corpus; or
  - open or link a conformance-fix task in this repo.
- Do not let implementation state silently redefine product or domain meaning.

## Dogfood promotion flow

- Promote `dev` to `dogfood` by PR when the integrated candidate is ready for live validation.
- Run full or feature-relevant E2E/live/dogfood checks on that promotion candidate, including acceptance-contract evidence mapped from `e2e/features/` and `e2e/scenario_mappings.json`.
- Add or update missing feature-relevant Gherkin/Cucumber scenarios or deterministic mappings by this stage at the latest.
- Merge to `dogfood` only when the candidate evidence is green or an accepted test-gate exception is documented.
- The merge to `dogfood` deploys or updates the persistent LAN dogfood stack for human, iPhone, and accessibility validation.
- Promote `dogfood` to `main` by PR only after green dogfood E2E/live evidence and required human-test signoff.

## Optional release-candidate hardening flow

- Use `rc/<version>` only when a named release needs extra hardening, packaging, release-note, or publication evidence beyond ordinary dogfood validation.
- `rc/*` is optional/later release hardening, not the ordinary human dogfood path. It must not bypass `dev` -> `dogfood` -> `main`.
- Tag releases from `main` after promotion.

## Hotfix flow

- Cut `hotfix/*` from `main` only for urgent stable-line fixes.
- Keep the hotfix narrow and evidence-backed.
- PR to `main` with the `main` exception explained in the PR body.
- Immediately backport or merge the accepted fix into `dev` and any affected active `rc/*` lane.

## No release-ready claim by default

A green PR or local gate is not a release-ready claim. Release readiness requires dogfood validation evidence, release notes, protected checks, and closure of named release blockers; optional `rc/*` evidence is added only when a named release hardening lane is used.
