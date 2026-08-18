# Documentation authority audit

Owner: [#1416](https://github.com/masssi164/weave/issues/1416)

This manifest separates current architecture truth from supporting, deferred, and historical material. A file not listed as active canonical must not override the canonical documents.

## Active canonical

These files define the current core:

- `README.md` — concise product entry point, status, and roadmap.
- `docs/architecture/data-sovereignty-core.md` — binding architecture and dependency direction.
- `docs/architecture/core-package-boundaries.md` — target package/module map and migration rules.
- `docs/architecture/canonical-transfer-kernel.md` — shared transfer, connector, checkpoint, and fidelity contract.
- `docs/development/core-workflow.md` — active development and PR workflow.
- `docs/testing/core-test-strategy.md` — active test ownership and E2E structure.
- `docs/documentation-audit.md` — this authority classification.

Canonical GitHub owners:

- #1299 — product/architecture mission;
- #1024 — architecture boundary;
- #1012 — canonical transfer kernel;
- #1320 — persistence;
- #1326 — Files/WebDAV;
- #1301 — Calendar/CalDAV;
- #1302 — Chat/Matrix;
- #1263 and #1415 — Files/Calendar MCP;
- #1014 — provider connector conformance;
- #1412 — system E2E;
- #1307 — DevOps truth.

## Active supporting

Supporting documents may explain a bounded implementation but cannot redefine the canonical authority model.

Keep only when they agree with the active canonical documents:

- protocol-specific WebDAV implementation notes;
- protocol-specific CalDAV/iCalendar implementation notes;
- bounded Matrix Client-Server profile notes;
- Flyway/JPA migration and recovery notes;
- standalone Compose/IAM/backup runbooks owned by #1304/#1306/#1412;
- generated API documentation explicitly classified as control/discovery/status convenience.

Every supporting file should link back to the relevant canonical owner issue and architecture document.

## Deferred

These topics remain valid future work but are not current core authority:

- named provider qualification and production cutover;
- migration of historical Nextcloud/Tuwunel data;
- Home-core integration;
- Matrix federation and Calls/MatrixRTC;
- client-owned multi-device Matrix E2EE lifecycle;
- Flutter and native OS integration;
- People/Contacts/CardDAV;
- Admin Console product work;
- TestFlight, public distribution, and commercial-release programs;
- broad Weaver memory/runtime policy beyond Matrix conversation and Files/Calendar MCP.

Deferred documents must start with a visible status header and link to their blocking issue. They must not appear in the README's current capability list.

## Historical archive candidates

The following known architecture generations are not current truth and should be moved below `docs/archive/` after content review:

- documents that define OpenAPI as Files, Calendar, Chat, or MCP data-plane authority;
- Candidate Cut and candidate-manifest operational guides;
- Fresh Start and downstream-Keycloak build plans;
- dogfood promotion and physical iPhone evidence plans;
- TestFlight and public-release evidence programs;
- human-readiness, approval-receipt, claim-matrix, and signoff systems;
- provider-first runtime or member-visible provider selection plans;
- Agent Runtime as a prerequisite for the Files/Calendar/Chat core;
- Calls, People, UI, or accessibility release matrices presented as Server/Data/MCP blockers;
- old multi-image/TestFlight/OpenTofu topology descriptions.

Known files requiring classification review include, where still present:

- `docs/architecture/adr-004-server-openapi-contract-authority.md`;
- `docs/architecture/adr-005-files-webdav-facade-slice.md`;
- `docs/architecture/domain-facade-protocol-projections.md`;
- `docs/architecture/weaver-openclaw-profile.md`;
- `docs/weave-contract-java-mcp.md`;
- `docs/governed-weaver-runtime-security-contract.md`;
- `docs/matrix-chat-migration-proof.md`;
- `docs/matrix-chat-sprint15-dry-run-policy.md`;
- sprint, release, dogfood, candidate, and evidence reports elsewhere under `docs/`.

A historical file retained for context must begin with:

```text
Status: historical archive; not current architecture or operational guidance.
Replacement: <link to current canonical document or issue>.
```

## Delete candidates

Delete rather than archive when a file is:

- a byte-for-byte or semantic duplicate;
- generated evidence for an obsolete confirmation gate;
- a stale screenshot-only claim;
- an abandoned sprint checklist with no enduring design information;
- a provider fixture report that does not drive executable connector tests;
- a copy of issue content with no independent repository value.

Deletion must not remove still-used test fixtures or migration history without first relocating their executable owner.

## Accuracy rules for all active documents

Active documentation must state consistently:

- canonical Weave data is the product authority;
- JPA/Flyway is persistence, not the domain model;
- external systems are source/target provider connectors;
- WebDAV, CalDAV/iCalendar, and Matrix are northbound data planes;
- MCP supports Files and Calendar only;
- Weaver/OpenClaw uses Matrix for Chat;
- transfer architecture is core now, named provider cutover later;
- no backward compatibility is promised for historical unreleased state;
- no universal lossless provider conversion is claimed;
- no Home-core integration is claimed as current work.

## Audit execution

The remaining repository-wide audit under #1416 must:

1. enumerate every Markdown/AsciiDoc documentation file;
2. assign exactly one classification;
3. verify active inbound links;
4. add archive headers before moving retained historical files;
5. delete pure duplicates;
6. scan active text for forbidden current claims;
7. verify every documented Gradle command exists;
8. perform a linear screen-reader review of README and the architecture overview.

The audit is not complete merely because this manifest exists. Completion requires the classified file inventory and corresponding moves/deletions in the same or follow-up pull requests.
