# Weave

**Your collaboration. Your choice of providers.**

Weave is building a modular collaboration platform for files, calendars, and conversations — without making one vendor the center of your working world. The goal is to keep your workspace, its identities, and its relationships while choosing the services behind each capability.

[Start developing](#develop-and-test) · [Roadmap](#ordered-roadmap) · [Architecture](#core-architecture) · [Meet Weaver](https://github.com/masssi164/weaver)

> **In development:** Weave is under active core reconstruction, not a finished production collaboration server. `dev` is the current implementation lane; `main` still represents the older architecture line.

## What Weave is

A collaboration platform should let you choose its building blocks, not require an all-or-nothing stack.

**Choose providers by capability.** Files, Calendar, and Chat have Weave-owned contracts. External services connect through replaceable adapters; their IDs, URLs, and database schemas do not become your workspace's identity. Provider-neutral does not mean that every provider is already supported.

**Keep the context, not just an export.** A file, an event, and a conversation belong to a working context. Weave's architecture gives collaboration data canonical identities, revisions, provenance, and transfer records rather than treating an export folder as the whole story. Unsupported conversions must be accounted for, not silently discarded.

**Use open interfaces.** WebDAV, CalDAV/iCalendar, and a bounded Matrix Client-Server profile are the core's client-facing standards. Weave can serve its canonical data natively or connect external systems through source, target, and reconciliation adapters.

The broader vision is a modular organization workspace, including documents, tasks, and meetings. The current reconstruction deliberately concentrates on **Files, Calendar, and Chat**. Calls, broad UI work, and named-provider production migrations are later work, not features promised by this README.

### Weave and Weaver

**Weave provides the collaboration foundation. [Weaver](https://github.com/masssi164/weaver) is its optional personal-assistant runtime.**

Weaver is an upstream-first OpenClaw distribution for a personal agent assigned to an entitled member. The organization controls available capabilities; Weave's domains remain responsible for authorization. The assistant is a way to work with the platform, not a prerequisite for using it.

The intended integration uses the Weave Matrix facade for conversation and the separate Weave MCP Server for Files and Calendar. **Chat is not duplicated as MCP tools.** Managed MCP in Weaver remains disabled until its workload-authorization contract is proven; the two projects' integration is not yet a production-readiness claim.

## Current status

The repository contains substantial implementation, tests, and infrastructure. The active work is bringing those pieces behind consistent provider-independent boundaries, not starting the product from scratch.

The current foundation covers architecture checks, canonical IDs and transfer envelopes, resumable checkpoints, deterministic idempotency keys, explicit loss accounting, and persistence adapters. Completion is assessed through the linked issue acceptance criteria and exact-commit tests, not through the presence of a class or a green build alone.

Work still being completed includes full domain/protocol conformance, Files/Calendar MCP equivalence, PostgreSQL-backed transfer checkpoints, provider connector conformance, and restart/backup/restore E2E. See the [binding roadmap](https://github.com/masssi164/weave/issues/1299) for current acceptance and dependencies.

There is no blanket claim of named-provider cutover readiness, Matrix federation, complete client E2EE, Home-core replacement, or public production readiness.

## Develop and test

Start from the implementation lane and run the checks relevant to your change:

```bash
git clone --branch dev https://github.com/masssi164/weave.git
cd weave
python3 tools/core_docs_check.py
```

Use **Java 21** for the foundation gates. Container tooling is needed for checks that actually start PostgreSQL, IAM, protocol, or full-system infrastructure.

```bash
./gradlew coreArchitectureCi
./gradlew canonicalDataCi
./gradlew postgresPersistenceCi
./gradlew protocolFacadeFoundationCi
./gradlew mcpFoundationCi
./gradlew coreDocsCheck
./gradlew coreCheck
```

These are developer checks, not a production installation procedure. Follow the [core development workflow](docs/development/core-workflow.md) for scope and prerequisites. Focused `protocolFacadeCi`, `providerConnectorCi`, `mcpFilesCalendarCi`, and `coreSystemE2e` are introduced by their owning issues when the full executable contracts exist.

Flutter, Node, Xcode, TestFlight, and manual release evidence are not prerequisites for unrelated Server/Data/MCP work.

## Core architecture

Weave separates **client protocols**, **collaboration behavior and canonical data**, and **persistence/provider implementations**.

```text
Clients and optional assistant integration
                  |
       WebDAV | CalDAV | Matrix
                  |
  Weave application use cases and authorization
          Files | Calendar | Chat
                  |
 Canonical identities, revisions and transfer rules
             /                  \
 Persistence adapters      Provider connectors
 PostgreSQL / BlobStore    Import / export / reconcile
```

In words: clients call Weave's protocol endpoints. Application services own identity, authorization, lifecycle, revisions, and transfer rules. Persistence adapters store canonical state; separate connectors handle external providers. Neither a provider SDK nor a persistence schema defines the product.

`weave-native` selects native boot composition, not a second implementation of each domain. OpenDAL supplies BlobStore infrastructure, iCal4j calendar syntax and recurrence, and Ruma/JNI Matrix protocol infrastructure.

OpenAPI is used for derived control, discovery, and convenience where appropriate; it is not the Files, Calendar, Chat, portability, or MCP data-plane authority. The separate MCP server reaches Weave through typed WebDAV/CalDAV clients, not direct persistence or provider access.

## Ordered roadmap

[Issue #1299](https://github.com/masssi164/weave/issues/1299) owns the binding sequence and acceptance criteria. The path is:

1. **Establish the core:** [architecture boundaries #1024](https://github.com/masssi164/weave/issues/1024), [canonical transfer kernel #1012](https://github.com/masssi164/weave/issues/1012), and [persistence adapters #1320](https://github.com/masssi164/weave/issues/1320).
2. **Complete the domain verticals:** [Files/WebDAV #1326](https://github.com/masssi164/weave/issues/1326), [Calendar/CalDAV #1301](https://github.com/masssi164/weave/issues/1301), and [Chat/Matrix #1302](https://github.com/masssi164/weave/issues/1302).
3. **Prove the integration boundaries:** [Files/Calendar MCP #1263](https://github.com/masssi164/weave/issues/1263), [MCP equivalence #1415](https://github.com/masssi164/weave/issues/1415), and [provider conformance #1014](https://github.com/masssi164/weave/issues/1014).
4. **Prove the system:** [standalone topology #1304](https://github.com/masssi164/weave/issues/1304), [IAM/topology #1306](https://github.com/masssi164/weave/issues/1306), [system E2E #1412](https://github.com/masssi164/weave/issues/1412), [CI truth #1307](https://github.com/masssi164/weave/issues/1307), and [documentation truth #1416](https://github.com/masssi164/weave/issues/1416).

The single `dev` to `main` convergence path remains [PR #1413](https://github.com/masssi164/weave/pull/1413). This summary does not replace the live issue state or introduce a new release schedule.

## Documentation

- **Understand the design:** [Data-sovereignty core](docs/architecture/data-sovereignty-core.md), [package boundaries](docs/architecture/core-package-boundaries.md), and [canonical transfer kernel](docs/architecture/canonical-transfer-kernel.md).
- **Work on the project:** [Development workflow](docs/development/core-workflow.md), [test strategy](docs/testing/core-test-strategy.md), and [contribution guide](CONTRIBUTING.md).
- **Check the evidence:** [Workflow disposition](docs/development/workflow-disposition.md), [documentation audit](docs/documentation-audit.md), and [CI runs](https://github.com/masssi164/weave/actions).

Historical documents are not current architecture authority. Superseded entry points redirect to the active documents above.

## Get involved

Bring a concrete collaboration problem, a reproducible issue, an interoperability test, or a focused contribution. Start with [the issues](https://github.com/masssi164/weave/issues) and agree on scope before a large change. Interface design, documentation, and installation feedback matter alongside code.

Weave is an independent project by Massimo. AI-assisted development and communication are welcome; human responsibility, review, and disclosure remain part of the contribution process.

## License

Copyright © 2026 Massimo (GitHub: masssi164).

Weave-authored code and documentation are licensed under **EUPL-1.2-or-later**, except where existing notices or documented third-party exceptions specify otherwise. See [LICENSE](LICENSE), [Notices](NOTICE.md), and [Third-party notices](THIRD_PARTY_NOTICES.md).

The vendored Matrix SDK crypto code and its upstream-oriented patch series retain their Apache-2.0 licensing and existing file-level notices. They are not relabelled as EUPL.

Contributors retain their copyright and follow the applicable licence and [DCO sign-off policy](CONTRIBUTING.md).
