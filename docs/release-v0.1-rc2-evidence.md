# v0.1.0-rc.2 release evidence

Status: published prerelease evidence, 2026-05-28.

## Release facts

- Release: [`v0.1.0-rc.2`](https://github.com/masssi164/weave/releases/tag/v0.1.0-rc.2)
- Published at: `2026-05-28T20:18:18Z`
- Candidate commit: `9409ae44a07f38864a45a203d90a842b22c6a82d` on protected `main`
- Tag target: `v0.1.0-rc.2` -> `9409ae44a07f38864a45a203d90a842b22c6a82d`
- Draft: `false`
- Prerelease: `true`
- Release-blocker state at publication: no open issues labeled `release-blocker`

## Requirement audit

The release-owner request was to merge the appropriate PRs and not call the work finished until a release existed. The audited state for `v0.1.0-rc.2` is:

- PR #344 was merged before the final candidate and is present on `main` as `2849c4b06cc75c1bc656c897d351050d33cbd281`.
- PR #382 was merged into `main` as `9409ae44a07f38864a45a203d90a842b22c6a82d` and is the RC2 candidate commit.
- Issue #381 was closed after the WEAVE-SPEC-0001 decision record was posted.
- Issue #379 recorded release-owner signoff for `v0.1.0-rc.2`, then closed after publication.
- No open PRs remained when the release audit was performed.

## Evidence reviewed

- PR-safe CI on `main`: [`26598506323`](https://github.com/masssi164/weave/actions/runs/26598506323), `success`, for `9409ae44a07f38864a45a203d90a842b22c6a82d`.
- Credentialed Live Stack E2E: [`26598646805`](https://github.com/masssi164/weave/actions/runs/26598646805), `success`, for `9409ae44a07f38864a45a203d90a842b22c6a82d`, artifact `weave-live-stack-acceptance-evidence`.
- Release draft workflow: [`26599654278`](https://github.com/masssi164/weave/actions/runs/26599654278), `success`, for `9409ae44a07f38864a45a203d90a842b22c6a82d`.
- Tag CI: [`26599820660`](https://github.com/masssi164/weave/actions/runs/26599820660), `success`, for `v0.1.0-rc.2` / `9409ae44a07f38864a45a203d90a842b22c6a82d`.
- Local release readiness check: `python3 tools/release_readiness_check.py --candidate-version 0.1.0-rc.2 --candidate-tag v0.1.0-rc.2 --candidate-commit 9409ae44a07f38864a45a203d90a842b22c6a82d --ci-summary build/evidence/ci-summary.json --live-evidence-dir /tmp/weave-live-stack-acceptance-evidence-26598646805 --release-notes docs/release-notes/v0.1.md --blockers-json build/evidence/release-blockers.json` returned `# RC readiness: ready` after the versioned release notes were updated.

## WEAVE-SPEC-0001 audit

The RC2 candidate includes the accepted WEAVE-SPEC-0001 baseline:

- product core: first-class Admin-Suite plus provider neutrality;
- member boundary: members see stable Weave capabilities only, not provider/status/admin internals;
- admin boundary: admins bind, unbind, validate, switch, and detach providers/adapters per domain;
- provider switching: guided plan, preflight, portable export/import, cutover, rollback/recovery;
- Weaver/AI runtime: explicitly out of scope for WEAVE-SPEC-0001.

The follow-up implementation DAG remains open by design and is not claimed as implemented by RC2:

- #386 — provider-neutral domain and capability model;
- #387 — Admin-Suite readiness and setup UX contract;
- #388 — provider switch and portable export/import contract;
- #389 — acceptance and evidence mapping.

## Documentation corrections from the RC2 audit

Tracking issue: #390.

The release itself was valid, but the post-release documentation needed tightening:

- versioned v0.1 notes now record the `v0.1.0-rc.2` publication facts and evidence links;
- `docs/release-notes/unreleased.md` is reset for post-RC2 work;
- README release-note markers are refreshed from the reset Unreleased page;
- Sprint 6 closure now has a post-closure note pointing to the actual RC2 release evidence;
- WEAVE-SPEC-0001 handoff tasks are marked complete for the spec PR merge/issue closure/release-evidence update.

This page is the compact audit trail for those corrections; the GitHub release remains the release artifact of record.
