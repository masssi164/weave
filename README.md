# Weave

**Your collaboration. Your choice of providers.**

Weave is building a collaboration product for files, calendars, and conversations with organization-owned identity, stable resource references, and replaceable providers. [Weaver](https://github.com/masssi164/weaver) is an optional, separately deployed personal-assistant runtime. Weave does not depend on Weaver or the owner's private Home-core collection to operate.

> **Development status:** This `main` branch contains an earlier foundation, not the accepted product consolidation. Follow [epic #1470](https://github.com/masssi164/weave/issues/1470) and its [implementation stories #1471–#1481](https://github.com/masssi164/weave/issues/1471) for the current delivery contract and evidence. The `dev` branch is the integration lane. A successful component build does not establish integrated product readiness.

## What Weave is

The approved release centers on one server-owned, code-first API with separate User and Admin OpenAPI artifacts and generated consumers in Flutter, MCP, the Admin UI, and product E2E. Server authorization must enforce the User/Admin, organization, and resource boundaries. Exactly one provider is active per organization and module.

Chat remains a native Matrix experience in Flutter and uses the established OpenClaw/Weaver Matrix integration. The release removes mandatory server-side Matrix facade/JNI coupling; it does not substitute a proprietary chat REST API. Files replacement must preserve data, stable references, and effective permissions across Nextcloud to optional native Files and verified rollback. Activation stops if a source property or permission cannot be preserved.

Public northbound WebDAV/CalDAV, private Runners, long-polling execution, workflows, context graphs, broad ARC orchestration, Calls, and multi-provider merging are outside this release. Southbound DAV remains an appropriate provider-adapter implementation detail. These boundaries describe the accepted target, not features already proven on `main`.

## Core architecture

The intended boundary is between Weave-owned product concepts and provider-specific implementation. Existing canonical IDs, provenance, journals, and transfer machinery are foundation work; the accepted provider switch and permission parity still require integrated proof.

## Current status

This branch contains prior Files, Calendar, and Chat domain foundations, persistence and transfer machinery, infrastructure, clients, and tests. Some older documents and architectural tests describe a superseded Core/protocol-first plan. Read them as implementation history or reusable technical material where [epic #1470](https://github.com/masssi164/weave/issues/1470) permits; they do not override the current delivery contract.

## Ordered roadmap

[Epic #1470](https://github.com/masssi164/weave/issues/1470) and its linked stories own scope, dependencies, and acceptance. The older Core issue order is historical and does not expand the approved release.

## Develop and test

For new work, use the protected `dev` integration lane and the [developer handbook](docs/developer-handbook.md). The [Gitflow workflow](docs/gitflow-pr-workflow.md) describes promotion to `main`. Java 21 is required for the Gradle foundation checks; container-backed checks require the corresponding local services.

```bash
./gradlew coreArchitectureCi canonicalDataCi postgresPersistenceCi
./gradlew protocolFacadeFoundationCi mcpFoundationCi coreDocsCheck
```

Run the relevant exact-head checks and integrated journeys before claiming readiness. The approved release also requires real User, Admin, MCP, and Files provider-switch evidence, including permission-preserving rollback and post-merge checks.

## Documentation

- [Data-sovereignty foundation](docs/architecture/data-sovereignty-core.md)
- [Canonical transfer kernel](docs/architecture/canonical-transfer-kernel.md)
- [Core development workflow](docs/development/core-workflow.md)
- [Documentation audit](docs/documentation-audit.md)
- [Weaver runtime](https://github.com/masssi164/weaver)

## License

Copyright © 2026 Massimo (GitHub: masssi164).

Weave-authored code and documentation are licensed under **EUPL-1.2-or-later**, except where existing notices or documented third-party exceptions specify otherwise. See [LICENSE](LICENSE), [Notices](NOTICE.md), [Third-party notices](THIRD_PARTY_NOTICES.md), and the [contribution and DCO guidance](CONTRIBUTING.md). Earlier licence grants remain in force. Vendored Matrix SDK crypto and its upstream-oriented patch series retain their Apache-2.0 and file-level notices.
