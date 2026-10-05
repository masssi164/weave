# Core development workflow

Status: active contributor entry point for the #1470 standalone product consolidation.

## Scope

Apply the pinned corpus `steering/release-2026-10-product-consolidation.md` and current
#1470 stories. Server code owns separate generated User/Admin HTTP contracts. Flutter,
Admin, MCP and product E2E consume their generated clients. Chat uses the bounded Weave
Matrix Client-Server facade and native clients. User/Admin isolation, current organization
and resource authorization, one member-facing sign-in and real provider-backed journeys
are required. Provider adoption/migration is tracked separately in #1498; public northbound
DAV, Calls, private execution and Home-core dependency are outside this release.

## Branch line

`dev` is the protected integration base. Use focused PRs and the
[lane-based protected workflow](../gitflow-pr-workflow.md). #1481 owns final verified
mainline delivery; historical convergence PRs are not automatic promotion authority.
A merge must neither publish a release nor mutate a live deployment without authorization.

## Issue ownership

- #1471: scope, specifications and existing-work disposition;
- #1472/#1473: server code-first API and generated consumers;
- #1474: supported identity and Weaver/MCP integration;
- #1475: Matrix facade, native clients and independent interoperability;
- #1476: provider-neutral domain references, bindings and authorization;
- #1479: usable Files/Calendar product surfaces;
- #1480: exact-candidate generation and real product acceptance;
- #1481: licensing, accurate documentation and protected mainline delivery;
- #1498, with #1477/#1478: provider adoption, migration and rollback.

Reuse earlier foundation source and tests according to #1470's disposition. Their older
priority labels and protocol assumptions do not expand current acceptance.

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

Domain code is framework-free. Application code depends inward and on ports. JPA implements
persistence ports. Providers implement the southbound ports. Files and Calendar use generated
User HTTP operations; their adapters may use DAV. Chat's northbound wire boundary is Matrix.
Curated MCP tools call the generated User client with current member/resource authorization.

`weave-native` belongs in boot composition, not in a second business implementation.

## Current commands

Use Java 21.

```bash
./gradlew coreArchitectureCi
./gradlew canonicalDataCi
./gradlew postgresPersistenceCi
./gradlew protocolFacadeFoundationCi
./gradlew mcpFoundationCi
./gradlew coreDocsCheck
./gradlew coreCheck
./gradlew generatedApiCi
```

`coreCheck` retains focused foundation coverage. `generatedApiCi` verifies the shared
code-first generation chain and actual JVM/Flutter/Admin consumers with pinned tools;
see its [execution contract](../../specs/generated-api-ci-contract.md). Its metadata export
requires no native Matrix runtime. Full Server protocol tests still build the required
Rust/Ruma/JNI library. Neither gate alone proves the real #1480 system journeys.

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
