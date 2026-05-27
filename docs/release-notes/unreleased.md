# Unreleased

Use this page for release-affecting changes that have merged but are not included in a tagged release yet.

## Added

- Repeatable RC readiness check with Markdown/JSON output, Gradle fixture tests, Live Stack evidence validation, release-blocker gating, and explicit waiver marker handling.
- MkDocs documentation site foundation with handbook navigation, diagrams, GitFlow/PR workflow, and release notes process.
- Root Gradle wrapper and orchestration tasks for delegated server, client, admin, infra, docs, acceptance, CI, and release-notes checks.
- Local release notes generator for merged PR metadata grouped by release-notes labels.
- Sprint 6 kickoff plan and initial Keycloak realm dry-run provider contract scaffold for admin-owned identity/provider operations.
- Enterprise release foundation with machine-checked release lanes, support-safe Live Stack evidence manifest, RC promotion/waiver contract, and required runtime marker policy.
- Workspace Health identity provider readiness facade with realm import, OIDC client, roles/groups, login, and policy cards backed only by backend Admin Console APIs.
- Admin/operator effective policy simulation endpoint with support-safe capability impact output, fail-closed unknown identity inputs, Weaver disabled-by-default evidence, and audited counts before provider or realm changes apply.
- Guarded identity realm apply decision path with explicit confirmation, retained-admin lockout protection, risky/destructive rollback evidence gates, support-safe audit counts, and decision-only provider mutation semantics.

## Changed

- Repositioned the root README as a Sprint 6 readiness/kickoff enterprise product entry point with audience-directed documentation navigation, explicit maturity status, Java 21 gate guidance, E2E/release-candidate evidence boundaries, and governed Weaver/AI PA boundaries.
- Added organization-embedding, identity-provisioning, and provider-replacement strategy contracts to make provider neutrality, mixed self-hosted/cloud/external deployments, and adapter replacement explicit before new feature slices.
- Documentation validation now has a dedicated docs check/build path.
- PR CI now enforces exactly one release-notes label before review/merge.
- `make release-notes-check` now verifies release-notes label edge cases and generator fixture output.
- Live Stack acceptance artifacts now include `release-evidence-manifest.json` with commit/run metadata, artifact list, support-safe exclusions, and the RC promotion rule.
- Admin Console treats missing identity readiness contracts as `admin-action-required` and keeps member flows provider-neutral/fail-closed during version skew.

## Fixed

- Nothing yet.

## Security

- Nothing yet.

## Accessibility

- Nothing yet.

## Migration/Operator Notes

- Operators can build the documentation site locally with `python3 -m pip install -r docs/requirements.txt` and `make docs-build`.

## Known Issues

- Nothing yet.
