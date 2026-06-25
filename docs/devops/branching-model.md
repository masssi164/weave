# Weave DevOps branching model

Weave uses a lane-based flow. The goal is a stable basis where the pinned specification corpus, implementation work, release candidates, and release truth are visible without turning daily work into heavy classic GitFlow.

## Lanes

- `main` is protected stable and release-capable truth.
  - It must contain dogfood-validated code and release evidence only.
  - Normal product work does not target `main` directly.
  - PRs to `main` normally promote `dogfood` after green dogfood E2E/live evidence and required human signoff; documented emergency `hotfix/*` exceptions are allowed.
  - Release tags are generated from `main`.
- `dev` is the protected integration lane.
  - Issue branches, bugfix branches, documentation branches, and spec-integration branches are cut from current `dev` and normally target `dev`.
  - Review/refactor loops, feature-specific tests, acceptance/Gherkin/Cucumber scenarios, docs/evidence, and PR-safe CI/contracts/unit/acceptance/docs gates happen here when possible.
  - `dev` is allowed to be ahead of the current release, but it must stay buildable and evidence-backed.
- `future/*` lanes hold larger product lines that are not release-ready yet.
  - They must keep spec reconciliation explicit.
  - They periodically PR back into `dev` in reviewable slices.
  - They are not a shortcut around release notes, linked issues, or acceptance evidence.
- `dogfood` is the persistent LAN test-stack and human dogfood lane.
  - Promote `dev` to `dogfood` by PR when a candidate is ready for live validation.
  - Full or feature-relevant Live Stack E2E, dogfood evidence, and missing scenario/mapping updates belong on this promotion by the latest.
  - Merging to `dogfood` deploys or updates the persistent LAN stack for human/iPhone/accessibility validation.
  - A green and signed-off `dogfood` candidate promotes to `main` by PR.
- `rc/<version>` lanes are optional later release-hardening lanes.
  - Cut them from `main` or `dogfood` only when a named release needs extra stabilization, release notes, packaging, or publication evidence beyond ordinary dogfood validation.
  - `rc/*` is not the normal human dogfood path and must not bypass `dev` -> `dogfood` -> `main`.
- `hotfix/*` lanes are emergency exceptions.
  - Cut them from `main` for urgent release-truth fixes.
  - PR them to `main`, then backport or merge the fix into `dev` so the lanes do not diverge silently.

## Issue branch naming

Use short-lived branches that show intent and owner area:

- `fix/<issue>-short-name`
- `feat/<issue>-short-name`
- `docs/<issue-or-topic>`
- `spec/<issue-or-topic>`
- `hotfix/<issue>-short-name`

## Required PR declarations

Every PR declares:

- target lane: `dev`, `future/*`, `rc/*`, or a `main` exception;
- linked issue or explicit spec/evidence note;
- release note text, or `Release note: none` with a reason;
- spec impact: none, updates spec, implements locked spec, or changes evidence only;
- gates run and evidence locations.

## Protection intent

Branch protection should make the lane names meaningful:

- protect `main`, `dev`, `dogfood`, and active `rc/*` branches;
- require reviews and green required checks before merge;
- require linear or squash history according to the repository merge policy;
- prevent direct pushes except for documented administrator recovery;
- keep release publication behind explicit approval.

This document records intended governance only. It does not mutate GitHub branch protection.
