# Unreleased

Use this page for release-affecting changes that have merged but are not included in a tagged release yet.

## Added

- MkDocs documentation site foundation with handbook navigation, diagrams, GitFlow/PR workflow, and release notes process.
- Root Gradle wrapper and orchestration tasks for delegated server, client, admin, infra, docs, acceptance, CI, and release-notes checks.
- Local release notes generator for merged PR metadata grouped by release-notes labels.
- Sprint 6 kickoff plan and initial Keycloak realm dry-run provider contract scaffold for admin-owned identity/provider operations.

## Changed

- Repositioned the root README as a Sprint 6 readiness/kickoff enterprise product entry point with audience-directed documentation navigation, explicit maturity status, Java 21 gate guidance, E2E/release-candidate evidence boundaries, and governed Weaver/AI PA boundaries.
- Added organization-embedding, identity-provisioning, and provider-replacement strategy contracts to make provider neutrality, mixed self-hosted/cloud/external deployments, and adapter replacement explicit before new feature slices.
- Documentation validation now has a dedicated docs check/build path.
- PR CI now enforces exactly one release-notes label before review/merge.
- `make release-notes-check` now verifies release-notes label edge cases and generator fixture output.

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
