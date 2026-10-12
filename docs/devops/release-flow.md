# Weave release flow

This release flow keeps `main` stable while giving Weave a practical integration lane for issues, a resettable Dogfood lane for exact automated qualification followed by distinct HIL, and optional later release-hardening lanes.

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
- Run deterministic checks and review on the promotion PR; add missing acceptance scenarios/mappings before qualification.
- The protected Dogfood merge commit becomes the candidate. Mandatory Full Compose, native macOS and native iOS Simulator integration must pass for that exact commit and pinned corpus revision.
- Deploy that same qualified commit and prepare its physical iPhone build before HIL. Merging to Dogfood alone does not authorize human-test readiness.
- Record explicit human acceptance; automated suites never manufacture human outcomes.
- Promote the unchanged qualified Dogfood tree to Main using existing automated, deployment, preparation and human evidence. Newer Dev work does not invalidate it; a changed candidate does.

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
