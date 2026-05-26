# Weave documentation

Weave is a provider-neutral organization suite and integration layer for chat, files, shared calendars, boards/tasks, meetings, decisions, and operator health. The documentation site is organized by the people who need it:

- [User Handbook](user-handbook.md) for members joining an already-provisioned organization.
- [Admin/Operator Handbook](admin-operator-handbook.md) for organization setup, provider selection, policies, readiness, audit, infra, and support bundles.
- [Developer Handbook](developer-handbook.md) and [GitFlow/PR workflow](gitflow-pr-workflow.md) for architecture, canonical models, facades/adapters, testing, release-note labels, release process, and contribution rules.
- [Diagrams](diagrams/index.md) for Mermaid domain and facade architecture sources.
- [v0.1 Golden Path readiness](v0.1-golden-path.md) for the professional-demo status map, evidence expectations, and reviewer checklist.
- [Release Notes](release-notes/index.md) for unreleased notes, v0.1 notes, categories, and operator-impact tracking.

## Current product truth

The active product direction is [Weave product line and Weaver integration plan](product-line-and-weaver-plan.md): Weave is product-first and provider-neutral. Admin/provider setup, IDM/RBAC, readiness, whitelisting, and support-safe diagnostics come before optional Weaver personal-assistant runtime work.

v0.1 is a dogfood-production release, not a preview. A normal member should see Weave-owned work surfaces and effective capability states, not raw provider configuration or provider secrets. The shortest status summary for reviewers is [v0.1 Golden Path readiness](v0.1-golden-path.md).

## Documentation stack

The site uses MkDocs with MkDocs Material. MkDocs Material is licensed under the MIT License, verified from the upstream `squidfunk/mkdocs-material` repository license on 2026-05-24, which is safe for this project's commercial and open-source usage.

Build locally with:

```sh
python3 -m pip install -r docs/requirements.txt
make docs-check
make docs-build
```
