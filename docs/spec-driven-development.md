# Spec-driven development for Weave

Status: active delivery framework, 2026-05-28.

Weave uses a lightweight Spec Kit-inspired workflow adapted to the existing monorepo, Gradle gates, Gherkin acceptance contracts, and repo-safe AI-assisted delivery model.

The rule is simple: **Git-versioned specs are truth; generated docs/wiki views are projections.**

## Why not a standalone wiki?

A separate manual wiki would drift from code, CI, and PR evidence. Weave may publish generated documentation from `specs/` and `docs/`, but the canonical source remains:

- `specs/` for versioned product/system contracts;
- `.specify/memory/constitution.md` for non-negotiable spec rules;
- `e2e/features/` and `e2e/scenario_mappings.json` for executable acceptance mapping;
- `build/evidence/` and CI artifacts for proof;
- GitHub issues/PRs for work tracking.

## Folder model

```text
.specify/
├── memory/constitution.md
└── templates/
    ├── weave-spec-template.md
    ├── weave-plan-template.md
    ├── weave-tasks-template.md
    └── weave-agent-briefs.md

specs/
├── README.md
└── 0000-weave-spec-framework/
    ├── spec.md
    ├── plan.md
    ├── tasks.md
    ├── traceability.yaml
    ├── contracts/
    ├── acceptance/
    └── evidence/
```

## Lifecycle

- `draft`: safe place for product questions and `[NEEDS CLARIFICATION: ...]` markers.
- `proposed`: reviewable direction, still allowed to carry explicit unresolved questions.
- `accepted`: contract approved; no clarification markers.
- `implementing`: active work; no clarification markers.
- `implemented`: implemented or merge-ready with evidence; no clarification markers.
- `superseded`, `deprecated`, `rejected`: historical states.

`./gradlew specContract` enforces required metadata and blocks clarification markers in implementation-ready specs. `./gradlew specContractTest` protects the guard itself with small fixtures.

## When full specs are required

Use full `spec.md` + `plan.md` + `tasks.md` for:

- new product capabilities;
- provider-neutral domain contracts;
- auth, IDM, RBAC, policy, whitelisting, or admin readiness;
- Weaver runtime integration;
- Gherkin/acceptance changes;
- API/event/provider contract changes;
- release-blocking evidence or architecture migrations.

For tiny bug fixes, a linked issue/spec note plus tests/evidence is enough unless the change touches product contracts.

## Product-core safety rule

Do not let assistants invent unresolved product decisions. If the answer changes the product core, keep the spec `draft` or `proposed` and write the uncertainty explicitly.

Product-core questions that require product owner/team confirmation include:

- first `WEAVE-SPEC-0001` product slice: organization embedding, Chat facade, identity policy, or another foundation;
- exact minimal domain vocabulary for the first user-visible release;
- which provider categories must be mandatory vs optional in v0.1/v0.2;
- when Weaver moves from placeholder category to governed runtime implementation;
- which agentic developer-assistance capability, if any, is allowed inside Weave policy.

## Sprint orchestration

A sprint is the execution of a spec-backed issue DAG, not a single implementation PR. The expected autonomous flow is:

1. The delivery lead recovers current truth from repo/GitHub/CI.
2. Product/spec and architecture/contract reviewers confirm whether the governing spec is implementable or needs clarification.
3. The co-leader creates or updates GitHub issues for the spec tasks, labels them `parallel` or `sequential`, and records dependencies in issue bodies.
4. Scoped implementers or reviewers work on narrow issues on short-lived branches.
5. The delivery lead opens PRs, assigns exactly one release-notes label, waits for green CI/gates, runs Integration-Gate, and merges in dependency order when authorized.
6. After each merge, the delivery lead updates `main`, revises remaining tasks if necessary, and continues.
7. Sprint closure records the spec, issue DAG, merged PR train, gates, evidence artifacts, unresolved product decisions, and next release/RC action.

Durable sprint state belongs in specs, issues, PRs, and checked-in reports. Agent chat/session state is disposable.

## DevOps traceability

Every implementation PR should connect:

```text
GitHub issue or spec note
  -> WEAVE-SPEC-ID and version
  -> acceptance scenario / mapping marker when product behavior changes
  -> test/contract/evidence gate
  -> sanitized CI evidence
  -> exactly one release-notes label
```

Minimum review evidence:

- `./gradlew specContract` and `./gradlew specContractTest` for spec/framework changes;
- `./gradlew acceptanceContract` when product behavior or Gherkin may be affected;
- the smallest area gate (`clientCi`, `serverCi`, `adminCi`, `infraStatic`, `docsStructureCheck`, etc.);
- `./gradlew ci` for cross-stack or release-relevant changes.

## AI-assisted delivery model

The delivery lead coordinates; scoped reviewers/implementers execute narrow tasks. Runtime configuration and allowlists are operator-owned outside this product repository. The repo-local workflow contract is [AI-assisted delivery orchestration](agent-team-orchestration.md).

Recommended review roles:

- **Product/spec steward**: product-core wording, lifecycle status, frontmatter, scope boundaries, open questions.
- **Client/accessibility specialist**: Flutter UX, screen-reader/Braille-friendly behavior, l10n, widget/semantics tests.
- **Server/domain-facade specialist**: canonical domain models, authorization, audit, provider boundaries, backend contracts.
- **Admin/policy specialist**: Admin Console, Workspace Health, IDM/RBAC, policy previews, readiness, whitelisting.
- **Provider/infra specialist**: OpenTofu, adapters, runner/env posture, backup/restore, support bundles.
- **QA/evidence specialist**: Gherkin, `scenario_mappings.json`, Live Stack evidence, sanitized artifacts.
- **Docs/release specialist**: docs navigation, handbooks, release notes, PR templates, closure reports.
- **Security/privacy specialist**: secrets, raw provider payloads, audit, support-safe diagnostics, external-provider risk.
- **Integration reviewer**: final PR readiness, diff scope, labels, gates, fallback review evidence.

## Assistant invocation rules

- Treat reviewer/implementer names as logical roles, not live runtime configuration.
- Do not add live agent allowlists, personal operator paths, model routing, private hierarchy definitions, or operator-runtime JSON examples to this product repo.
- Do not use Copilot review as a blocker while premium review requests are exhausted. Record fallback human/assistant review plus green CI.
- Do not paste old transcripts into assistants. Give them exact specs/docs, allowed files, required gates, and stop conditions.
- Run Integration-Gate/Optimization-Review loops until there is no material improvement opportunity or a product-core clarification blocks safe progress.
- Stop before secrets, live infra mutation, data loss, history rewrite, hidden scope expansion, or unresolved product-core ambiguity.

## First rollout

1. Land the framework and `WEAVE-SPEC-0000`.
2. Choose `WEAVE-SPEC-0001` only after product-core confirmation.
3. Run the first real feature through: spec -> plan -> tasks -> acceptance mapping -> implementation -> evidence -> PR.
4. Generate any wiki/docs view from repo files after the pattern proves useful.
