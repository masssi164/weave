# AI-assisted delivery orchestration

Status: active product-repo delivery guidance, 2026-05-29.

This page defines repo-safe rules for AI-assisted Weave delivery. It describes product-repository workflow expectations only. Live coding-agent configuration, allowlists, hierarchy, model routing, personal operator paths, and runtime policy belong outside this repository.

## Operating principle

A delivery lead owns issue/PR orchestration, merge sequencing, integration, and evidence. The lead does not become a mega-coder and must not declare a sprint complete just because one PR is green.

Use this loop until the issue DAG is integrated or a real blocker stops safe progress:

1. Recover truth from `main`, repo specs/docs/tasks, GitHub issues/PRs, and CI/evidence.
2. Identify the governing spec and decide whether it is ready for implementation. Keep unresolved product-core questions in `draft`/`proposed`.
3. Convert the spec plan/tasks into a GitHub issue DAG. Label independent work `parallel`; label ordered work `sequential`; link issues/PRs back to the spec.
4. Cut short-lived branches from updated `main` and open reviewable PRs in dependency order.
5. Assign scoped implementation or review work only for narrow issue slices, each with exact files, inputs, stop conditions, and a required gate.
6. Integrate only from returned evidence and inspected diffs, then run an adversarial Integration-Gate review.
7. Merge dependency-free PRs once CI/gates/review evidence pass and branch protection permits; otherwise surface the exact missing approval or check.
8. After every merge, update `main`, re-evaluate the remaining DAG, and continue the next slice.
9. Stop only when all sprint issues/PRs are merged or when a product-core clarification, live-infra/destructive approval, or failed gate blocks safe progress.
10. Produce the sprint closure report from repo/GitHub/CI evidence.

Material optimization means improved correctness, traceability, security/privacy, accessibility, supportability, deployability, CI/evidence quality, or reviewability without hidden scope expansion.

## Sprint management responsibilities

Durable sprint state belongs in GitHub and repo files, not chat transcripts:

- Specification stewarding lives in `specs/<id>/spec.md`, `plan.md`, `tasks.md`, and traceability files.
- Work tracking lives in GitHub issues with dependency notes and `parallel`/`sequential` labels.
- Implementation state lives in PRs and CI artifacts.
- Release impact lives in exactly one release-notes label per PR.
- Sprint closure lives in a checked-in report or PR comment that cites merged PRs, gates, and remaining decisions.

## Recommended review roles

Use the smallest role set that covers the slice. Role names are logical responsibilities, not runtime configuration:

- Product/spec: product-core wording, lifecycle status, frontmatter, non-goals, clarification markers, issue slicing.
- Architecture/contract: provider-neutral domain boundaries, contract drift, spec/plan architecture consistency.
- Client/accessibility: Flutter UX, screen-reader behavior, keyboard navigation, l10n, widget tests.
- Server/domain: domain facades, authorization, audit, provider boundaries, backend contract tests.
- Admin/policy: Admin Console, Workspace Health, IDM/RBAC, policy previews, readiness, whitelisting.
- Provider/infra: adapters, OpenTofu, runner/environment posture, backup/restore, support bundles.
- QA/evidence: Gherkin, scenario mappings, sanitized artifacts, Live Stack E2E posture.
- Docs/release: docs navigation, handbooks, release notes, PR templates, closure reports.
- Security/privacy: secrets, raw provider payloads, audit, support-safe diagnostics, external-provider risk.
- Integration review: final diff/spec/PR readiness, evidence strength, scope creep, release-label and CI posture.

## Runtime boundary

The product repo intentionally does not contain live agent runtime configuration.

Allowed repo-local content:

- product/spec workflow rules;
- generic handoff templates;
- evidence expectations;
- review-role descriptions;
- CI/release gates.

Forbidden repo-local content:

- live agent allowlists;
- personal operator paths or local account names;
- model/provider routing for personal agents;
- private hierarchy definitions;
- operator-runtime JSON examples that look deployable.

If an operator changes live coding-agent configuration, they must do it in the operator-owned configuration layer, not through a Weave product PR.

## Brief and handoff contracts

Use `.specify/templates/weave-agent-briefs.md` for compact repo-safe templates:

- Truth-Recovery
- Specialist-Brief
- Evidence-Return
- Integration-Gate
- Optimization-Review
- Session-Handoff

A worker prompt must include:

- role and one independently testable goal;
- exact allowed files or globs;
- governing spec/plan/tasks/docs paths;
- required gate or reason the gate cannot run;
- stop conditions;
- evidence-only return format.

Do not paste transcripts, broad sprint histories, hidden product assumptions, live runtime config, or private operator context into workers.

## Professional evaluation rubric

Score 0-3:

- correctness against spec and acceptance;
- traceability from issue/spec to evidence;
- product-core protection and explicit unresolved questions;
- handoff usability;
- runtime-policy hygiene;
- maintainability and simplicity;
- evidence quality and reproducibility.

A score below 2 in any release-blocking dimension requires another implementation loop or an explicit blocker.

## Guardrails

Stop before:

- secrets or raw provider payload exposure;
- live infra mutation without approval;
- data loss or destructive cleanup;
- history rewrite;
- hidden scope expansion;
- accepted/implementing/implemented specs that still contain `[NEEDS CLARIFICATION: ...]`;
- product-core choices that the product owner/team have not decided.

When blocked by product-core ambiguity, keep the spec `draft` or `proposed`, write the clarification marker, and ask for the one decision needed to proceed.
