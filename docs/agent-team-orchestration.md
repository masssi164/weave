# AI-assisted delivery orchestration

Status: active product-repo delivery guidance, 2026-10-19.

This page defines repo-safe rules for AI-assisted Weave delivery. It describes product-repository workflow expectations only. Live coding-agent configuration, allowlists, hierarchy, model routing, personal operator paths, and runtime policy belong outside this repository.

## Operating principle

A delivery lead owns issue/PR orchestration, merge sequencing, integration, and evidence. The lead does not become a mega-coder and must not declare a sprint complete just because one PR is green.

A prepared bounded task uses the root `AGENTS.md` truth-recovery and completion rules. The lead supplies a source-pinned brief with governing requirements, settled decisions, scope, acceptance, checks and stop conditions. Workers inspect the affected code and recover only missing or changed material inputs. Optional improvements do not extend an accepted task. Whole-sprint discovery and closure are not inherited by a leaf fix.

For an explicitly assigned full sprint, use this loop until the issue DAG is integrated or a real blocker stops safe progress:

1. Establish source-pinned sprint truth once from the governing corpus, integration lanes, issue DAG and evidence. Reuse the prepared state for workers; refresh only missing or materially changed inputs and consequential live checks.
2. Identify the governing corpus spec and decide whether it is ready for implementation. Keep unresolved product-core questions in `draft`/`proposed`.
3. Convert the spec plan/tasks into a GitHub issue DAG. Label independent work `parallel`; label ordered work `sequential`; link issues/PRs back to the spec.
4. Cut normal short-lived branches from updated `dev` (urgent stable-line hotfixes start from `main`) and open reviewable PRs in dependency order.
5. Assign scoped implementation or review work only for narrow issue slices, each with exact files, inputs, stop conditions, and a required gate.
6. Integrate only from returned evidence and inspected diffs, then run an adversarial Integration-Gate review.
7. Merge dependency-free PRs once CI/gates/review evidence pass and branch protection permits; otherwise surface the exact missing approval or check.
8. After a Dev merge, update the integration identity and review its relevant delta. Adjust only affected DAG nodes or decisions, then continue the next scoped slice without repeating whole-project discovery.
9. Stop only when all sprint issues/PRs are merged or when a product-core clarification, live-infra/destructive approval, or failed gate blocks safe progress.
10. Produce the sprint closure report from repo/GitHub/CI evidence.

Apply the finite acceptance loop and blocking-finding definition in `.specify/templates/weave-agent-briefs.md`. A bounded task does not inherit the full sprint closure gate, and optional improvements never authorize scope expansion.

## Sprint management responsibilities

Durable sprint state belongs in GitHub and repo files, not chat transcripts:

- Specification stewarding lives in the pinned spec corpus; repo-local `specs/<id>/spec.md`, `plan.md`, `tasks.md`, and traceability files are transitional conformance artifacts.
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
- Provider/infra: adapters, Compose profiles, Keycloak desired-state reconciliation, runner/environment posture, backup/restore, support bundles.
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
- implementation commit/tree, corpus pin, governing requirements and source paths;
- settled decisions and the changed inputs that invalidate them;
- observable acceptance and an explicit completion boundary;
- required checks and review, with unavailable prerequisites reported;
- stop conditions and action authorization;
- evidence-only return format.

Keep current blockers separate from historical failures and already integrated repairs. Bind check results to their actual source; report pending CI or independent review explicitly and refresh consequential live permissions/checks before acting. Do not paste transcripts, broad sprint histories, hidden product assumptions, live runtime config, or private operator context into workers.

## Professional evaluation rubric

Optional explanatory scores (0-3):

- correctness against spec and acceptance;
- traceability from issue/spec to evidence;
- product-core protection and explicit unresolved questions;
- handoff usability;
- runtime-policy hygiene;
- maintainability and simplicity;
- evidence quality and reproducibility.

A concrete violation of an applicable release-blocking requirement requires correction or an explicit blocker. Explain it with the affected behavior, source and required fix. A score alone or an optional improvement does not require another implementation loop; satisfied scope acceptance and required gates end the task.

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
