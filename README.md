# Weave

**Your collaboration. Your choice of providers.**

Weave is building a modular collaboration platform for files, calendars, and conversations — without making one vendor the center of your working world. The goal is to keep your workspace, its identities, and its relationships while choosing the services behind each capability.

[Start developing](#develop-and-test) · [Roadmap](#ordered-roadmap) · [Architecture](#core-architecture) · [Meet Weaver](https://github.com/masssi164/weaver)

> **In development:** Weave is not a finished production collaboration platform. `dev` is the current implementation lane; `main` still represents the older architecture line.

## What Weave is

A collaboration platform should let you choose its building blocks, not require an all-or-nothing stack.

**Choose providers by capability.** Files, Calendar, and Chat are product concepts, not vendor names. Replaceable adapters connect the services behind them. Provider-neutral does not mean every provider is already supported.

**Keep the context, not just an export.** A file, an event, and a conversation can belong to the same Space. Stable `weave://` references and cross-domain relationships are central to the design: changing a provider should not mean rebuilding every link around your work. A real transfer must account for unsupported data and rollback limits rather than promise magical losslessness.

**Build the workspace you need.** The broader vision includes documents, tasks, and meetings as modular capabilities. The immediate implementation work concentrates on the Files, Calendar, and Chat foundation and its integration boundaries. The wider vision is not a list of already shipped features.

### Weave and Weaver

**Weave provides the collaboration foundation. [Weaver](https://github.com/masssi164/weaver) is its optional personal-assistant runtime.**

Weaver is an upstream-first OpenClaw distribution for a personal agent assigned to an entitled member. It is intended to work within organization-approved capabilities and current domain permissions — not become a second identity system or an unrestricted shortcut to provider credentials.

Weave does not require an agent to be useful. Weaver adds another way to work with it. Matrix supplies the conversational channel; workload-scoped MCP is a separately gated integration. **Managed MCP is currently disabled in Weaver**, and Chat is not duplicated as MCP tools.

## Current status

This is an existing codebase with substantial implementation, tests, and infrastructure, undergoing architectural consolidation. A passing component test or build is not evidence that every planned integration is ready for daily use.

The active [architecture epic #1299](https://github.com/masssi164/weave/issues/1299) tracks provider-neutral resources, real Files cutover and rollback, and a planned private execution plane. Protocol conformance, authorization, restart, and backup/restore must be proven for the actual candidate.

Some older implementation documents and the pinned specification snapshot do not yet match that newer roadmap. [Reconciliation #1469](https://github.com/masssi164/weave/issues/1469) tracks the discrepancy explicitly; this README neither changes the specification pin nor presents roadmap proposals as completed runtime behavior.

## Develop and test

Start from the implementation lane:

```bash
git clone --branch dev https://github.com/masssi164/weave.git
cd weave
python3 tools/core_docs_check.py
```

Use **Java 21** for the foundation gates below. Container tooling is needed for checks that actually start PostgreSQL, IAM, protocol, or full-system infrastructure. Run the checks relevant to your change; these are developer commands, not a production installer.

```bash
./gradlew coreArchitectureCi
./gradlew canonicalDataCi
./gradlew postgresPersistenceCi
./gradlew protocolFacadeFoundationCi
./gradlew mcpFoundationCi
./gradlew coreDocsCheck
./gradlew coreCheck
```

Follow the [core development workflow](docs/development/core-workflow.md) for scope and prerequisites. The current branch also documents future focused gates named `protocolFacadeCi`, `providerConnectorCi`, `mcpFilesCalendarCi`, and `coreSystemE2e`; do not assume they are available until their owning implementation lands.

Flutter, Node, Xcode, TestFlight, and manual release evidence are not prerequisites for unrelated Server/Data/MCP work.

## Core architecture

The enduring boundary is between **the meaning of your workspace** and **the services implementing its capabilities**.

```text
People and their interfaces       Optional Weaver assistant
                 \                 /
                  Weave product layer
       Spaces | stable resources | relationships
       access intent | provider bindings | evidence
                              |
                   Integration boundaries
                              |
                 Files | Calendar | Chat
                     Selected providers
```

In words: Weave connects collaboration capabilities through its own product concepts and stable references. Provider-specific identifiers and behavior stay at adapter boundaries. Weaver remains an optional runtime integration; it does not define the collaboration platform or replace its authorization.

The current roadmap also explores private outbound Runners for organization-local capabilities. That is a distinct execution component, not another name for Weaver, and not a capability advertised as ready here.

Detailed protocol and persistence choices belong to the [pinned specification policy](docs/specification-source-of-truth.md) and the owning implementation tasks. See [#1469](https://github.com/masssi164/weave/issues/1469) before treating older architecture documents as the reconciled target.

## Ordered roadmap

[Issue #1299](https://github.com/masssi164/weave/issues/1299) owns the current implementation sequence and acceptance criteria:

1. **Kernel and persistence:** [boundaries #1024](https://github.com/masssi164/weave/issues/1024), [resources and migration #1012](https://github.com/masssi164/weave/issues/1012), and [persistence #1320](https://github.com/masssi164/weave/issues/1320).
2. **Collaboration integration:** [Files #1326](https://github.com/masssi164/weave/issues/1326), [Calendar #1301](https://github.com/masssi164/weave/issues/1301), [Matrix/Chat #1302](https://github.com/masssi164/weave/issues/1302), and [MCP #1263](https://github.com/masssi164/weave/issues/1263) / [#1415](https://github.com/masssi164/weave/issues/1415).
3. **Real portability and deployment proof:** [Files cutover #1014](https://github.com/masssi164/weave/issues/1014), [identity #1304](https://github.com/masssi164/weave/issues/1304), [topology #1306](https://github.com/masssi164/weave/issues/1306), and [system E2E #1412](https://github.com/masssi164/weave/issues/1412).
4. **Private execution:** [Runner contracts #1452](https://github.com/masssi164/weave/issues/1452), [task and lease authority #1453](https://github.com/masssi164/weave/issues/1453), [context #1454](https://github.com/masssi164/weave/issues/1454), [MCP projection #1455](https://github.com/masssi164/weave/issues/1455), and [reference E2E #1456](https://github.com/masssi164/weave/issues/1456).

[CI #1307](https://github.com/masssi164/weave/issues/1307) and [documentation #1416](https://github.com/masssi164/weave/issues/1416) accompany the implementation. The documented `dev` to `main` convergence path remains [PR #1413](https://github.com/masssi164/weave/pull/1413); this README does not promote that work or introduce a release date.

## Documentation

- **Start contributing:** [Development workflow](docs/development/core-workflow.md), [test strategy](docs/testing/core-test-strategy.md), and [contribution guide](CONTRIBUTING.md).
- **Read implementation notes:** [Data-sovereignty core](docs/architecture/data-sovereignty-core.md), [package boundaries](docs/architecture/core-package-boundaries.md), and [transfer kernel](docs/architecture/canonical-transfer-kernel.md). Read these with the reconciliation note above.
- **Check the evidence:** [Workflow disposition](docs/development/workflow-disposition.md), [documentation audit](docs/documentation-audit.md), and [CI runs](https://github.com/masssi164/weave/actions).

## Get involved

Bring a concrete collaboration problem, a reproducible issue, an interoperability test, or a focused contribution. Start with [the issues](https://github.com/masssi164/weave/issues) and agree on scope before a large change. Interface design, documentation, and installation feedback matter alongside code.

Weave is an independent project by Massimo. AI-assisted development and communication are welcome, with human responsibility, review, and disclosure.

## License

Copyright © 2026 Massimo (GitHub: masssi164).

Weave-authored code and documentation are licensed under **EUPL-1.2-or-later**, except where existing notices or documented third-party exceptions specify otherwise. See [LICENSE](LICENSE), [Notices](NOTICE.md), and [Third-party notices](THIRD_PARTY_NOTICES.md).

The vendored Matrix SDK crypto code and its upstream-oriented patch series retain their Apache-2.0 licensing and existing file-level notices. They are not relabelled as EUPL. Contributors retain copyright and follow the applicable licence and [DCO sign-off policy](CONTRIBUTING.md).
