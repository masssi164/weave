# Weave agent rules

Weave is one monorepo. `client/`, `server/`, `infra/`, `e2e/`, `docs/`, and `release/` ship as one product.

Old `weave-backend` and `weave-infra` checkouts are stale. Ignore them.

Truth split: the canonical fachliche specification truth is the pinned Weave Specification Corpus in `specs/weave-specs.lock.json` (local default `../weave-specs`). This repo is implementation/evidence truth: code, tests, CI, release evidence, GitHub issues/PRs/milestones, and generated or transitional spec projections. If corpus and repo reality disagree, create an explicit spec-change or conformance-fix task; never let implementation state silently redefine product/domain meaning.

Language policy: every `AGENTS.md`, agent prompt, checked-in project instruction, PR body, issue body, code comment, and documentation change must be written in English unless a user-facing localization file explicitly requires another language.

Product-line truth: read `docs/product-line-and-weaver-plan.md` before product direction, admin/provider, RBAC/whitelist, or Weaver work. Preserve the order: Weave provider-neutral organization suite first; admin portal/IDM/RBAC/readiness/whitelisting second; Weaver governed per-user PA runtime later. Do not regress to agent-first planning or a fixed Nextcloud/Matrix-only product boundary.

v0.1 is dogfood-production, not preview. No scaffold, roadmap, or coming-soon UX in normal member paths.

## Required project standards

Before coding, opening PRs, merging, or declaring work complete, read and follow:

- `docs/developer-handbook.md` for coding conventions, Gradle gates, generated code, accessibility/i18n, and evidence expectations.
- `docs/gitflow-pr-workflow.md` for protected `main`, short-lived branches, PR readiness, merge rules, and release-notes labels.
- `docs/weave-operating-model.md` for delivery lifecycle and governance.
- `specs/weave-specs.lock.json`, the pinned spec corpus files, `.specify/memory/constitution.md`, `docs/spec-driven-development.md`, and transitional repo-local `specs/` for conformance context.
- Domain docs near the changed area (`client/`, `server/`, `infra/`, `e2e/`, `docs/`).

Default gates: `./gradlew specCorpusConformance`, `./gradlew acceptanceContract`, `./gradlew clientCi`, `./gradlew serverCi`, `./gradlew adminCi`, `./gradlew infraStatic`, `./gradlew docsCheck`, and `./gradlew releaseEvidenceCheck`; use the smallest meaningful subset and `./gradlew ci` for cross-stack changes.

## Autonomous sprint ownership

An assigned delivery lead owns Weave sprint or milestone work end to end. Scoped reviewers and implementers receive narrow, evidence-based briefs through the operator environment; the product repo must not encode live assistant hierarchy, allowlists, model routing, or personal operator paths.

A user request such as “finish Sprint N”, “take this milestone”, or “close the sprint” is sufficient. The user does not need to repeat acceptance criteria. The delivery lead must derive acceptance from GitHub milestone/issues, linked PRs, repo-local specs/tasks, developer docs, CI policy, and existing evidence.

A sprint-finish request authorizes normal repository delivery actions needed to finish the sprint: creating/updating issues, labels, branches, PRs, checked-in docs/evidence, PR comments, fallback reviews, merges into protected `main` when gates pass, issue closure, and milestone closure. It does not authorize secrets disclosure, destructive data loss, live infrastructure mutation, history rewrite, production release publication, or unresolved product-core decisions.

## Autonomous sprint loop

When assigned a sprint or milestone, the delivery lead must run this loop without handing decomposition back to the requester:

1. **Truth recovery**: identify governing spec corpus files first, then fetch current `origin/main`, inspect GitHub milestone/issues/PRs/checks, read linked repo specs/tasks/docs as conformance artifacts, and identify the governing sprint lifecycle.
2. **Acceptance derivation**: turn each issue/spec/task into testable acceptance criteria and evidence gates. If an issue lacks acceptance, infer it from linked specs/docs and update the issue or sprint plan instead of asking the user for restatement.
3. **Issue DAG**: build or repair the dependency graph; mark independent work parallel and ordered work sequential.
4. **Delegation**: brief scoped reviewers or implementers with strict repo-safe inputs: task id, allowed files/globs, relevant docs, derived acceptance, required gates, output contract, and stop conditions.
5. **File ownership**: never let parallel specialists edit the same files without explicit sequencing or separate worktrees/branches.
6. **PR train**: create short-lived branches from current `origin/main`, open issue-scoped PRs, fill the PR template, and apply exactly one `release-notes-*` label.
7. **Quality gates**: run local gates, inspect GitHub CI, use fallback human/agent review when Copilot review is unavailable, and fix failures before continuing.
8. **Merge progression**: merge PRs in dependency order when gates pass and the sprint-finish authorization covers normal merges; after each merge, fetch/fast-forward `main` before the next dependent branch.
9. **Closure report**: create/update `docs/sprint-<n>-closure-report.md` with governing specs, issue DAG final state, merged PRs in order, gates/CI evidence, release/RC impact, unresolved decisions, and next safe action.
10. **GitHub closure gate**: verify via GitHub that the target milestone has zero open issues, all sprint issues are closed, the closure report exists on `origin/main`, and the final `main` CI after the last merge is green.

Do not stop after one PR. Do not report success from local git state, a green branch, or a raw diff. If the closure gate fails, report `BLOCKED` with the exact issue, PR, command, check, missing decision, or permission that prevents progress.

## Work routing

- `client/`: Flutter UX, accessibility, l10n.
- `server/`: facades, authz, audit, provider boundaries.
- `admin-console/`: admin UX, readiness, policy preview, setup flows.
- `infra/`: OpenTofu, deploy, backup/restore, support bundles.
- `e2e/`: Gherkin contracts and evidence mapping.
- `docs/`: current product truth, release notes, handbooks, closure reports.

Use compact templates from `.specify/templates/weave-agent-briefs.md`: Truth-Recovery, Specialist-Brief, Coding-Harness-Brief, Evidence-Return, Integration-Gate, Optimization-Review, and Session-Handoff. Use `docs/agent-team-orchestration.md` for repo-safe AI-assisted delivery guidance.

## Hard stops

Stop and ask only for product-core decisions not inferable from issues/specs, external communication, destructive data loss, live infra mutation, secrets/raw provider payloads, history rewrite, production release publication, hidden scope expansion, or gates that fail after a concrete fix attempt.

Accessibility, supportability, auditability, and deployability are release blockers.

## Global Weave agent baseline

- Write agent instructions, PRs, issues, code comments, and documentation in English unless an explicit localization file requires another language.
- Follow `docs/developer-handbook.md`, `docs/gitflow-pr-workflow.md`, `docs/weave-operating-model.md`, and relevant domain docs before coding, opening PRs, merging, or declaring work complete.
- If the user asks to finish a sprint/milestone, derive acceptance first from the pinned spec corpus, then from GitHub issues/milestones, repo conformance specs/tasks, docs, CI policy, and evidence; do not require the user to restate issue acceptance criteria.
- Keep live assistant runtime configuration, allowlists, hierarchy, model routing, and personal operator paths outside this product repo.
- Use protected `main`, short-lived branches, exactly one `release-notes-*` label per PR, smallest meaningful local gates, green CI, fallback review evidence, and GitHub closure verification before reporting completion.
