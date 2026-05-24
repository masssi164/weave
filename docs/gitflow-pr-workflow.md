# GitFlow and PR workflow

Weave uses short-lived PR branches on top of `main`. Keep changes spec-driven: intent -> issue/spec note -> acceptance/evidence -> implementation -> review.

## Branch and PR rules

1. Start from current `origin/main`.
2. Create a focused branch named for the scope, for example `docs/mkdocs-handbook-foundation`, `feat/admin-policy-profiles`, or `fix/chat-empty-state`.
3. Keep unrelated local files and assistant workspace files out of commits.
4. Open a PR early enough for CI and review, but mark it draft if it is not review-ready.
5. Before requesting review, run the smallest meaningful local gate and record it in the PR body.
6. Request GitHub Copilot review on every review-ready PR.
7. Do not merge until CI is green and the release-notes label gate passes.

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
- Fill the PR template, including user/admin/operator impact and checks run.
- Note contract/spec changes or explicitly mark that there are none.
- For UI-facing changes, include accessibility and localization impact.
- For docs/release process changes, run `make docs-check`; use `make release-notes-check` alone only for release-note page or label-policy edits that do not need a site build.
- Request Copilot review with `gh pr edit <number> --add-reviewer @copilot` or through the GitHub UI.

## CI enforcement

The `Release Notes Label Check` job reads PR labels and fails unless exactly one of the release-notes labels is present. Add the label before pushing review-ready updates when possible. The workflow runs on PR open, synchronize, ready-for-review, label, and unlabel events so fixing labels reruns the check.

The docs gate also checks that release-note pages and diagram navigation remain present. `make release-notes-check` additionally tests zero-label and multiple-label failures plus the release-notes generator fixture.

## Merge expectations

A PR is merge-ready only when:

- it has exactly one release-notes label;
- Copilot review was requested for the review-ready PR;
- required CI is green or an explicit accepted exception is documented;
- acceptance/evidence changes are mapped where product behavior changed;
- no unrelated local, generated, secret, or OpenClaw agent files are included.
