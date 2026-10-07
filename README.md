# Weave

**Your collaboration. Your choice of providers.**

Weave is building a collaboration product for files, calendars, and conversations with organization-owned identity, stable resource references, and replaceable providers. [Weaver](https://github.com/masssi164/weaver) is an optional, separately deployed personal-assistant runtime. Weave does not depend on Weaver or the owner's private Home-core collection to operate.

> **Development status:** This `main` branch contains an earlier foundation, not the accepted product consolidation. Follow [epic #1470](https://github.com/masssi164/weave/issues/1470) and its current child stories for the standalone product contract. [Epic #1498](https://github.com/masssi164/weave/issues/1498) owns provider migration and rollback. The `dev` branch is the integration lane. A successful component build does not establish integrated product readiness.

## What Weave is

The approved release centers on one server-owned, code-first HTTP API with separate User and Admin OpenAPI artifacts and generated consumers in Flutter, MCP, the Admin UI, and product E2E. Server authorization enforces User/Admin, organization, and resource boundaries. Exactly one provider is active per organization and module.

Chat uses a bounded, standards-compliant Matrix Client-Server facade northbound in Weave. Rust/Ruma/JNI handles its Matrix wire boundary; the Weave Chat domain owns authorization, canonical relationships, and provider-neutral routing. Flutter keeps its native Rust/Matrix SDK, and Weaver keeps its established OpenClaw Matrix integration. The [Matrix support profile](docs/reference/matrix-client-server-support-profile.md) defines the release surface; Weave does not claim to be a general-purpose Matrix homeserver. One ordinary Weave sign-in must make authorized Files, Calendar, and Chat capabilities ready without provider-specific Connect, login, or token steps.

Provider adoption, data migration, permission-preserving cutover, reconciliation, and rollback belong to [epic #1498](https://github.com/masssi164/weave/issues/1498). Public northbound WebDAV/CalDAV, private Runners, long-polling execution, workflows, context graphs, broad ARC orchestration, Calls, and multi-provider merging are outside #1470. Southbound DAV remains an appropriate provider-adapter implementation detail. These boundaries describe the accepted target, not features already proven on `main`.

## Core architecture

The intended boundary is between Weave-owned product concepts and provider-specific implementation. The Server owns the User/Admin HTTP contract, stable resource identities, current authorization, and one active provider binding per organization and module. The Matrix Client-Server facade is the explicit non-OpenAPI northbound exception for Chat. Existing transfer machinery is foundation work for #1498; provider replacement and permission parity still require separate integrated proof.

## Current status

This branch contains prior Files, Calendar, and Chat domain foundations, persistence and transfer machinery, infrastructure, clients, and tests. The generated-client, single-sign-in, Matrix facade, and real product journeys required by #1470 are still being integrated and verified. Some older documents and architectural tests describe superseded product assumptions; they do not override the current [release steering contract](https://github.com/masssi164/weave-specs/blob/main/steering/release-2026-10-product-consolidation.md).

## Ordered roadmap

[Epic #1470](https://github.com/masssi164/weave/issues/1470) and its current child stories own standalone product scope, dependencies, and acceptance. [Epic #1498](https://github.com/masssi164/weave/issues/1498) owns provider portability and migration. The older Core issue order is historical and does not expand either release.

## Develop and test

For new work, use the protected `dev` integration lane and the [developer handbook](docs/developer-handbook.md). The [Gitflow workflow](docs/gitflow-pr-workflow.md) describes promotion to `main`. Java 21 is required for the Gradle foundation checks; container-backed checks require the corresponding local services.

```bash
./gradlew coreArchitectureCi canonicalDataCi postgresPersistenceCi
./gradlew protocolFacadeFoundationCi mcpFoundationCi coreDocsCheck
```

Run the relevant exact-head checks and integrated journeys before claiming readiness. #1470 requires real Flutter single-sign-in, User/Admin, Matrix, Files, Calendar, and MCP journeys plus post-merge checks. Provider-switch and permission-preserving rollback evidence closes #1498 separately.

## Documentation

- [Data-sovereignty foundation](docs/architecture/data-sovereignty-core.md)
- [Canonical transfer kernel](docs/architecture/canonical-transfer-kernel.md)
- [Core development workflow](docs/development/core-workflow.md)
- [Documentation audit](docs/documentation-audit.md)
- [Weaver runtime](https://github.com/masssi164/weaver)

## License

Copyright © 2026 Massimo (GitHub: masssi164).

Weave-authored code and documentation are licensed under **EUPL-1.2-or-later**, except where existing notices or documented third-party exceptions specify otherwise. See [LICENSE](LICENSE), [Notices](NOTICE.md), [Third-party notices](THIRD_PARTY_NOTICES.md), and the [contribution and DCO guidance](CONTRIBUTING.md). Earlier licence grants remain in force. Vendored Matrix SDK crypto and its upstream-oriented patch series retain their Apache-2.0 and file-level notices.
