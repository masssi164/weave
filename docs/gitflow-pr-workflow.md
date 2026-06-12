# Lane-based PR and release workflow

Weave uses clear DevOps lanes instead of heavy classic GitFlow: `main` for stable release truth, `dev` for integration, `future/*` for larger not-yet-release-ready lines, `rc/*` for release candidates and Live Stack E2E evidence, and `hotfix/*` for urgent stable-line fixes. Keep changes spec-driven: intent -> issue/spec note -> acceptance/evidence -> implementation -> review. For the full delivery contract, see [Weave operating model](weave-operating-model.md) and the DevOps docs under `docs/devops/`.

## Branch and PR rules

1. Start from the correct current lane: normally `origin/dev`, `future/*` for large future lines, `rc/*` for candidate stabilization, or `origin/main` only for emergency hotfixes.
2. Create a focused branch named for the scope, for example `docs/mkdocs-handbook-foundation`, `feat/admin-policy-profiles`, or `fix/chat-empty-state`.
3. Keep unrelated local files and assistant workspace files out of commits.
4. Open a PR early enough for CI and review, but mark it draft if it is not review-ready.
5. Before requesting review, run the smallest meaningful local gate and record it in the PR body.
6. Declare target lane, linked issue, release-note line or none reason, spec impact, and gates run.
7. Request GitHub Copilot review on every review-ready PR.
8. Do not merge until protected checks are green, conversations are resolved, and the release-notes label gate passes.


## Dev, testing, staging, and production

- `dev` is a branch lane for integration; it is not a deployed environment.
- Local development and temporary previews still use developer machines or disposable worktrees.
- Testing/staging is represented by GitHub Environments or workflow targets, especially for release-candidate and Live Stack E2E evidence.
- Release candidates use `rc/<version>` branches cut from `dev`; release tags are generated from `main` after promotion.
- Production releases use final SemVer tags such as `vX.Y.Z` plus explicit production approval.
- A merge to `main` makes Weave release-capable; it is not an automatic production deploy.

## Mandatory release-notes label policy

Every PR must deliberately choose exactly one release notes label before review/merge:

| Label | Use when | Release notes behavior |
| --- | --- | --- |
| `release-notes-feature` | The PR adds or changes user, admin/operator, developer, docs, infrastructure, or product behavior worth mentioning. | Included in generated release notes under Added/Changed-style sections. |
| `release-notes-bugfix` | The PR fixes a bug, regression, broken docs, failed gate, supportability issue, or release-blocking defect. | Included in generated release notes under Fixed. |
| `release-notes-skip` | The PR has no release-facing impact, such as pure refactors, formatting-only changes, dependency metadata with no user/operator effect, or test-only maintenance. | Excluded from generated release notes. |

The labels are mutually exclusive. A PR with zero or multiple `release-notes-*` labels is not review-ready and must fail the CI label gate.

Release notes are generated from merged PR labels, not manually reconstructed later. Use `tools/release_notes_generate.py` to draft `docs/release-notes/unreleased.md` from merged GitHub PR metadata; issue #293 tracks release-draft/GitHub release publishing automation beyond the local generator.

## PR creation checklist

Before review-ready:

- Link the issue or spec note that explains intent and acceptance criteria.
- Choose exactly one release-notes label.
- Fill the PR template, including target lane, release-note line or none reason, spec impact, user/admin/operator impact, and checks run.
- Note contract/spec changes or explicitly mark that there are none.
- For UI-facing changes, include accessibility and localization impact.
- For docs/release process changes, run `make docs-check`; use `make release-notes-check` alone only for release-note page or label-policy edits that do not need a site build.
- Request Copilot review with `gh pr edit <number> --add-reviewer @copilot` or through the GitHub UI.

## CI enforcement

The `Release Notes Label Check` job reads PR labels and fails unless exactly one of the release-notes labels is present. Add the label before pushing review-ready updates when possible. The workflow runs on PR open, synchronize, ready-for-review, label, and unlabel events so fixing labels reruns the check.

The docs gate also checks that release-note pages and diagram navigation remain present. `make release-notes-check` additionally tests zero-label and multiple-label failures plus the release-notes generator fixture.

## Merge expectations

A PR is merge-ready only when:

- it declares target lane, spec impact, release-note line or none reason, and exactly one release-notes label;
- Copilot review was requested for the review-ready PR;
- required CI is green or an explicit accepted exception is documented;
- acceptance/evidence changes are mapped where product behavior changed;
- no unrelated local, generated, secret, or OpenClaw agent files are included.
