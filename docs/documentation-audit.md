# Documentation audit

Status: active index for issue #1416.

## Classification

- **active canonical**: binding current truth;
- **active supporting**: accurate detail subordinate to canonical docs;
- **future/deferred**: valid later scope that does not block the core;
- **historical**: project history only;
- **delete/redirect**: contradictory or duplicate content removed while old paths point to replacements.

Git history remains the archive of removed prose.

## Active canonical

- `README.md`;
- `docs/architecture/data-sovereignty-core.md`;
- `docs/architecture/core-package-boundaries.md`;
- `docs/architecture/canonical-transfer-kernel.md`;
- `docs/development/core-workflow.md`;
- `docs/testing/core-test-strategy.md`;
- `docs/documentation-audit.md`.

## Active supporting

These remain useful only when consistent with the canonical docs:

- `AGENTS.md`, pending a separate command/sprint audit;
- `docs/architecture/database-schema-authority.md`, subject to #1320;
- domain protocol documents describing executable WebDAV, CalDAV, or Matrix behavior;
- security documents limited to accepted OIDC, authorization, redaction, and secret boundaries.

## Redirected authority

This change replaces these paths with redirects:

- `docs/architecture.md`;
- `docs/bootstrap-foundation-contract.md`;
- `docs/architecture/adr-004-server-openapi-contract-authority.md`;
- `docs/architecture/adr-006-enterprise-hard-plan-decision-lock.md`;
- `docs/architecture/adr-007-persistence-entity-strategy.md`;
- `docs/architecture/canonical-domains.md`;
- `docs/architecture/domain-facade-protocol-projections.md`;
- `docs/architecture/provider-and-infrastructure-boundaries.md`.

They contained OpenAPI authority, provider-first/native-provider terminology, code-first schema authority, broader enterprise scope, or duplicate explanations.

## Future/deferred

Flutter/native OS, physical-device accessibility/distribution, Calls/MatrixRTC, People/CardDAV, named providers, Home-core integration, commercial readiness, public release/TestFlight, and advanced Agent Runtime/Weaver memory do not block current Server/Data/MCP work.

## Historical workflows

Candidate Cut, Fresh Start, dogfood promotion, sprint evidence, manual validation manifests, release claim matrices, marketing screenshots, and physical iPhone workflows are historical for the current core. #1307 owns their workflow/task disposition and removal.

## Remaining work

Audit remaining release notes/evidence, sprint/dogfood plans, client/platform acceptance, Agent Runtime governance, provider lab/commercial readiness, old operator handbooks, MkDocs navigation, and generated assets. A file is not authority merely because it has not yet moved.
