# Core development workflow

Status: active contributor entry point for Server, canonical data, protocol, persistence, provider connector, and MCP work.

## Scope

The current core covers Files, Calendar, Chat, and Files/Calendar MCP. Client, Calls, release, TestFlight, Home-core, and named-provider work do not block it.

## Branch line

Until convergence:

- `dev` is the implementation base;
- `main` is still the older architecture line;
- PR #1413 is the only `dev` to `main` convergence path.

Foundation work is stacked in this order: architecture boundary, canonical transfer kernel, CI/documentation truth, main convergence, persistence, then domain verticals.

## Issue ownership

- #1024: dependency boundaries;
- #1012: canonical transfer kernel;
- #1320: Flyway/JPA persistence;
- #1326: Files/WebDAV;
- #1301: Calendar/CalDAV;
- #1302: Chat/Matrix;
- #1263 and #1415: Files/Calendar MCP;
- #1014: provider conformance;
- #1304 and #1306: standalone IAM/topology;
- #1412: final E2E;
- #1307: CI and obsolete workflow removal;
- #1416: documentation.

## Code placement

For a core domain, target packages are:

```text
<domain>/domain
<domain>/application
<domain>/port/inbound
<domain>/port/persistence
<domain>/port/provider
<domain>/projection/<standard>
<domain>/adapter/persistence/jpa
<domain>/adapter/provider/<provider>
<domain>/adapter/infrastructure/<technology>
<domain>/boot
```

Domain code is framework-free. Application code depends inward and on ports. JPA implements persistence ports. External systems implement source/target provider ports. WebDAV, CalDAV, Matrix, and MCP are northbound projections.

`weave-native` belongs in boot composition, not in a second business implementation.

## Current commands

Use Java 21.

```bash
./gradlew coreArchitectureCi
./gradlew canonicalDataCi
./gradlew :server:test
./gradlew :server:postgresJpaTest
./gradlew :weave-mcp-server:test
```

`coreCheck`, focused protocol/connector/MCP jobs, and `coreSystemE2e` are introduced under #1307 as they become truthful.

Do not require Flutter, Node, MkDocs, Xcode, TestFlight, screenshots, or manual release evidence for unrelated Server/Data/MCP changes.

## Change sequence

1. State the owning issue and invariant.
2. Add or strengthen the smallest boundary/contract test.
3. Introduce canonical types and ports before concrete adapters.
4. Add JPA/provider/protocol implementations behind those ports.
5. Run focused tests.
6. Run affected aggregate gates.
7. Update active documentation only when the executable contract changed.
8. Open a small PR and document follow-up deletion of transitional code.

## Pull requests

- Do not bypass protected checks.
- Use stacked PRs only when the dependency is explicit.
- Retarget a stacked PR after its base merges.
- Keep incomplete CI or migration work as draft.
- Never use stale evidence from another tree.
- Do not preserve unreleased compatibility solely to avoid deleting historical code.

## Evidence

Tests may report exact commit, dependency/runtime versions, schema/model/transfer versions, sanitized service inventory, counts, hashes, checkpoints, fidelity totals, and restore identifiers.

They must not report credentials, bearer tokens, private content, provider payloads, private host paths, decorative screenshots, or manual approval attestations.
