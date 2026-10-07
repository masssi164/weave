# Documentation audit

Status: historical #1416 audit snapshot. Issue #1416 is superseded by #1471/#1470;
the labels and priorities below record that earlier inventory and are not current
product or release authority. For current work, read the pinned corpus through
[Specification source of truth](specification-source-of-truth.md), then
[Core development workflow](development/core-workflow.md),
[Current workflow ownership](development/current-workflow-ownership.md),
[Developer handbook](developer-handbook.md), and the current #1470 child stories.
Provider migration belongs to #1498. Native Flutter/Matrix and physical-device
validation are active #1475/#1480 evidence, not historical release extras.

## Historical classification

- **active canonical**: binding current truth;
- **active supporting**: accurate detail subordinate to canonical docs;
- **future/deferred**: valid later scope that does not block the core;
- **historical**: project history only;
- **delete/redirect**: contradictory or duplicate content removed while old paths point to replacements.

Git history remains the archive of removed prose.

## Former active-canonical list

- `README.md`;
- `docs/architecture/data-sovereignty-core.md`;
- `docs/architecture/core-package-boundaries.md`;
- `docs/architecture/canonical-transfer-kernel.md`;
- `docs/development/core-workflow.md`;
- `docs/testing/core-test-strategy.md`;
- `docs/documentation-audit.md`.

## Former active-supporting list

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

## Former future/deferred list

The #1416 snapshot grouped Flutter/native OS and physical-device accessibility with Calls/MatrixRTC, People/CardDAV, Home-core integration, and other later work. That grouping is superseded: native Flutter, Matrix, accessibility, and single-sign-in validation are current #1475/#1480 requirements; Calls and public DAV remain later work.

## Historical workflows

Candidate Cut, Fresh Start, old sprint ceremony, and marketing workflows were historical in this snapshot. Current protected CI, exact-candidate Full Compose E2E, and physical-device validation are described by [Current workflow ownership](development/current-workflow-ownership.md); #1480 owns their acceptance, and #1481 owns verified mainline delivery.

## Former remaining-work list

Audit remaining release notes/evidence, sprint/dogfood plans, client/platform acceptance, Agent Runtime governance, provider lab/commercial readiness, old operator handbooks, MkDocs navigation, and generated assets. A file is not authority merely because it has not yet moved.
