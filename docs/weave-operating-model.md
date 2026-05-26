# Weave operating model

This page is the compact working contract for Weave delivery. It keeps sprint, release, and agent work aligned without turning prompts or handbooks into project journals.

## Source of truth

- Current implementation truth lives in this monorepo, GitHub issues/PRs, protected-branch status, and executable CI/evidence artifacts.
- Historical chat, agent transcripts, local memories, and old checkouts are orientation only; verify them before acting.
- Sprint state belongs in GitHub issues, PRs, and closure reports, not in bootstrap prompts.
- Product and architecture decisions belong in repo docs; PR bodies carry current acceptance/evidence for one slice.

## Branch and release model

- `main` is the only long-lived integration branch and must remain release-capable.
- Work uses short-lived feature, fix, docs, or CI branches from current `origin/main`.
- Do not introduce long-lived `dev`, `develop`, `testing`, `staging`, or `release/*` branches as the primary flow.
- Every PR targets `main`, stays focused, and carries exactly one release-notes label:
  - `release-notes-feature`
  - `release-notes-bugfix`
  - `release-notes-skip`
- Release candidates are tags on `main` such as `vX.Y.Z-rc.N`.
- Production releases are final SemVer tags such as `vX.Y.Z` plus explicit production approval.
- A merge to `main` makes Weave release-capable; it does not by itself publish production.

## Environments

- `local/dev` is a developer machine, temporary preview, or disposable worktree; it is not a durable branch.
- `testing`/`staging` is a GitHub Environment or workflow target for release-candidate verification and Live Stack E2E evidence.
- `production` is a GitHub Environment guarded by manual approval and release evidence.
- Live Stack E2E is release evidence, not a replacement for local or PR-safe gates.

## PR readiness

A PR is review-ready only when it has:

- a linked issue, spec, or acceptance note;
- exactly one release-notes label;
- a filled PR body with scope, user/operator/developer impact, evidence, and risks;
- the smallest meaningful local gate recorded;
- Copilot review requested or an explicit fallback noted;
- no unrelated local, generated, secret, or assistant workspace files.

A PR is merge-ready only when protected checks are green, required conversations are resolved, Copilot findings or fallback review are addressed, and the branch is mergeable.

## Agent orchestration

`weave-co-leader` is the orchestrator for Weave sprint and release work. It should not behave like a mega-coder.

Use this loop:

1. Recover current truth from repo, GitHub, and CI.
2. Select the smallest next slice.
3. Spawn scoped specialists only when useful.
4. Require evidence-only returns.
5. Run the integration gate before PR handoff or merge.
6. End with a compact session handoff.

Regular specialist scopes:

- Client: Flutter, accessibility, localization, widget/semantics tests.
- Server: facades, authorization, audit, provider boundaries, backend contracts.
- DevOps/release: Gradle, GitHub Actions, branch protection, release labels, environments.
- QA/evidence: acceptance contracts, Live Stack E2E, dogfood evidence, sanitized artifacts.
- Docs/release: release notes, sprint reports, developer docs, PR templates.
- Review: architecture risk, scope creep, merge readiness.

## Five reusable agent templates

Keep the template names stable and the actual prompt short.

- Truth-Recovery: reconstruct current branch, PRs, issues, CI, blockers, and next actions from evidence.
- Specialist-Brief: assign one scoped task, allowed files, required gate, and stop conditions.
- Evidence-Return: return done/evidence/verification/blocked/recommended-next without transcript recap.
- Integration-Gate: inspect PR, checks, reviews, mergeability, blockers, and recommendation.
- Session-Handoff: preserve durable state, references, blockers, next safe action, and discard notes.

Do not paste old transcripts, full sprint histories, broad architecture essays, stale status, or duplicate policy text into agent prompts.

## Sprint closure

A sprint is not complete until:

- planned scope is either merged, explicitly deferred with issue links, or declared a non-goal;
- protected `main` checks are green for the relevant head;
- required release notes/evidence are generated or explicitly marked not applicable;
- final E2E/dogfood evidence is present or an accepted release-gate exception is recorded;
- a sprint closure report exists with PR graph, CI/evidence, known non-goals, and follow-ups.
