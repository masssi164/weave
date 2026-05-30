# Weave documentation

Weave is a provider-neutral organization operating layer for chat, files, shared calendars, boards/tasks, meetings, decisions, manuals, and operator health. Documentation is organized by audience first so readers do not have to reverse-engineer the product from sprint reports.

## Choose your path

### Evaluator / product reviewer

Start with:

1. [v0.1 Golden Path readiness](v0.1-golden-path.md) — what is available, guarded, disabled by policy, degraded, unavailable, or future.
2. Root README — public product entry point and architecture-at-a-glance.
3. [Product acceptance flows](product-acceptance-flows.md) — product-language acceptance paths.
4. [v0.1.0-rc.2 release evidence](release-v0.1-rc2-evidence.md) — exact candidate commit, gates, signoff, and post-release documentation corrections.
5. [Sprint 6 closure report](sprint-6-closure-report.md) — final RC/provider-ops foundation evidence and first-release entry criteria, with a post-closure pointer to RC2.
6. [Sprint 5 closure report](sprint-5-closure-report.md) — project-readiness evidence and release-candidate gaps.
7. [Enterprise release foundation](enterprise-release-foundation.md) — release lanes, RC gate, waiver semantics, and support-safe artifacts.
8. [Sprint 6 epic closure report](sprint-6-epic-closure-report.md) — #212/#233 acceptance evidence after the merged identity/admin slices.

### Member / user

Start with:

1. [User Handbook](user-handbook.md) — joining and using an already-provisioned organization.
2. [v0.1 Golden Path readiness](v0.1-golden-path.md) — status language for available, disabled-by-policy, not-configured, degraded, unavailable, coming-later, and admin-setup-required surfaces.
3. [Accessibility release gate](accessibility-release-gate.md) — keyboard, screen-reader, and non-color-only expectations.

### Owner / admin

Start with:

1. [Admin/Operator Handbook](admin-operator-handbook.md) — setup, provider categories, policy, readiness, audit, backup/restore, and support bundles.
2. [Admin-Suite readiness and setup contract](admin-suite-readiness-setup-contract.md) — guided setup assistant, readiness dashboard, action boundary, and support-safe evidence.
3. [Admin-provisioned first use](admin-provisioned-first-use.md) — member/admin boundary and setup acceptance.
4. [Meeting architecture decision record](meeting-architecture-decision.md) — contextual meeting attach points, E2EE boundaries, consent defaults, and fail-closed readiness.
5. [Organization embedding contract](organization-embedding-contract.md) — identity, tenant, roles/groups, non-human identities, and future Weaver category boundaries.
6. [Identity provisioning strategy](identity-provisioning-strategy.md) — LDAP/AD/OIDC/SAML/SCIM-oriented identity planning.

### Operator

Start with:

1. [Admin/Operator Handbook](admin-operator-handbook.md) — operational entry point.
2. [Control-plane infra bootstrap](control-plane-infra-bootstrap.md) — infra/control-plane foundation.
3. [Provider replacement and anti-silo contract](provider-replacement-and-anti-silo-contract.md) — dry-run, migration, export/delete, rollback, and evidence requirements.
4. [Quality and acceptance evidence](quality-and-evidence.md) — evidence handling and sanitized artifacts.
5. [Enterprise release foundation](enterprise-release-foundation.md) — release-candidate promotion, live evidence, and waiver contract.

### Developer / contributor

Start with:

1. [Developer Handbook](developer-handbook.md) — local prerequisites, Java 21+ requirement, Gradle gates, client/server/admin/infra workflows, and evidence expectations.
2. [Trunk-based PR and release workflow](gitflow-pr-workflow.md) — branch, review, label, release-note, and merge rules.
3. [Spec-driven development for Weave](spec-driven-development.md) — repo-local specs, lifecycle, evidence gates, and agent orchestration.
4. [AI-assisted delivery orchestration](agent-team-orchestration.md) — repo-safe roles, handoff briefs, runtime-boundary guardrails, and optimization loop.
5. [Canonical feature models](canonical-feature-models.md) — provider-neutral domain vocabulary.
6. [Accessible workflow context contract](workflow-context-contract.md) — linear workflow primitives, context references, agent dry-run rules, and the MVP slice before a visual builder.
7. [Architecture](architecture.md) and [Diagrams](diagrams/index.md) — facades, data flow, and domain diagrams.

### Security / compliance reviewer

Start with:

1. [Organization embedding contract](organization-embedding-contract.md) — roles, groups, non-human identities, audit, break-glass, and provider category boundaries.
2. [Provider replacement and anti-silo contract](provider-replacement-and-anti-silo-contract.md) — migration, export/delete, lossy mapping, and rollback expectations.
3. [Quality and acceptance evidence](quality-and-evidence.md) — support-safe evidence and leak-prevention rules.
4. [Roadmap and guarded surfaces](roadmap-and-guarded-surfaces.md) — what is intentionally not yet claimed.

## Documentation maturity map

Canonical product docs:

- Root README
- [v0.1 Golden Path readiness](v0.1-golden-path.md)
- [Weave product line and Weaver integration plan](product-line-and-weaver-plan.md)
- [Spec-driven development for Weave](spec-driven-development.md)
- [AI-assisted delivery orchestration](agent-team-orchestration.md)
- [Canonical feature models](canonical-feature-models.md)
- [Accessible workflow context contract](workflow-context-contract.md)
- [Meeting architecture decision record](meeting-architecture-decision.md)
- [Architecture](architecture.md)
- [Canonical domains](architecture/canonical-domains.md)
- [Canonical domain registry v1](domain-registry-v1.md)
- [Provider portability contract](architecture/provider-portability.md)
- [Space anchor contract](space-anchor-contract.md)
- [Weaver OpenClaw-derived runtime profile](architecture/weaver-openclaw-profile.md)
- [Organization embedding contract](organization-embedding-contract.md)
- [Admin-Suite readiness and setup contract](admin-suite-readiness-setup-contract.md)
- [Provider replacement and anti-silo contract](provider-replacement-and-anti-silo-contract.md)

Canonical handbooks:

- [User Handbook](user-handbook.md)
- [Admin/Operator Handbook](admin-operator-handbook.md)
- [Developer Handbook](developer-handbook.md)
- [GitFlow/PR workflow](gitflow-pr-workflow.md)

Release and evidence docs:

- [Release notes](release-notes/index.md)
- [Quality and acceptance evidence](quality-and-evidence.md)
- [Build evidence delivery system](build-evidence-delivery-system.md)
- [Enterprise release foundation](enterprise-release-foundation.md)
- [Manuals and release notes integration](manuals-and-release-notes-integration.md)
- [Sprint 5 closure report](sprint-5-closure-report.md)
- [Sprint 6 kickoff plan](sprint-6-kickoff-plan.md)
- [Sprint 6 epic closure report](sprint-6-epic-closure-report.md)
- [v0.1.0-rc.2 release evidence](release-v0.1-rc2-evidence.md)
- [Sprint 6 closure report](sprint-6-closure-report.md)
- [Sprint 8 delivery board policy](project/sprint-8-delivery-board.md)

Historical/context docs:

- Sprint reports and strategy notes are useful provenance, but they are secondary to the canonical product docs above.
- Research notes under `docs/research/` are evidence inputs, not current product promises.
- [Roadmap and guarded surfaces](roadmap-and-guarded-surfaces.md) records future or guarded areas without promoting them as shipped.

## Current product truth

The active product direction is [Weave product line and Weaver integration plan](product-line-and-weaver-plan.md): Weave is product-first and provider-neutral. Admin/provider setup, IDM/RBAC, readiness, whitelisting, and support-safe diagnostics come before optional Weaver personal-assistant runtime work.

Weaver remains optional, governed, auditable, support-safe, and disabled by default. Any future per-user PA runtime must be generated from Weave organization policy as an isolated OpenClaw-derived profile and follow the rule: user-rights, organization-whitelisted capabilities. See [Governed Weaver runtime security contract](governed-weaver-runtime-security-contract.md) for the runtime/model/tool-provider split, approval receipts, and support-safe evidence boundary.

v0.1 is dogfood-production, not a preview. A normal member should see Weave-owned work surfaces and effective capability states, not raw provider configuration or provider secrets. The shortest status summary for reviewers is [v0.1 Golden Path readiness](v0.1-golden-path.md). The latest published prerelease audit is [v0.1.0-rc.2 release evidence](release-v0.1-rc2-evidence.md).

## Documentation stack

The site uses MkDocs with MkDocs Material. MkDocs Material is licensed under the MIT License, verified from the upstream `squidfunk/mkdocs-material` repository license on 2026-05-24, which is safe for this project's commercial and open-source usage.

Build locally with:

```sh
python3 -m pip install -r docs/requirements.txt
make docs-check
make docs-build
```
