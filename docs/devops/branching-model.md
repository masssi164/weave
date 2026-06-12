# Weave DevOps branching model

Weave uses a lane-based flow. The goal is a stable basis where the pinned specification corpus, implementation work, release candidates, and release truth are visible without turning daily work into heavy classic GitFlow.

## Lanes

- `main` is protected stable and release truth.
  - It must contain release-ready code and release evidence only.
  - Normal product work does not target `main` directly.
  - PRs to `main` are limited to promoted `rc/*` branches or documented emergency `hotfix/*` exceptions.
  - Release tags are generated from `main`.
- `dev` is the protected integration lane.
  - Issue branches, bugfix branches, documentation branches, and spec-integration branches normally target `dev`.
  - `dev` is allowed to be ahead of the current release, but it must stay buildable and evidence-backed.
  - CI/spec gates run here before release-candidate cutting.
- `future/*` lanes hold larger product lines that are not release-ready yet.
  - They must keep spec reconciliation explicit.
  - They periodically PR back into `dev` in reviewable slices.
  - They are not a shortcut around release notes, linked issues, or acceptance evidence.
- `rc/<version>` lanes are release-candidate and E2E lanes.
  - Cut them from `dev` when the candidate scope is ready.
  - Only stabilization fixes, release evidence, and release-note corrections belong here.
  - Full Live Stack E2E and release-evidence gates run here before promotion.
  - A green and reviewed `rc/*` branch promotes to `main` by PR.
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

- protect `main`, `dev`, and active `rc/*` branches;
- require reviews and green required checks before merge;
- require linear or squash history according to the repository merge policy;
- prevent direct pushes except for documented administrator recovery;
- keep release publication behind explicit approval.

This document records intended governance only. It does not mutate GitHub branch protection.
