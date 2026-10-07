# Weave

**Your collaboration. Your choice of providers.**

Weave is building a modular collaboration platform for files, calendars, and conversations — without making one vendor the center of your working world. The goal is to keep your workspace, its identities, and its relationships while choosing the services behind each capability.

[Start developing](#develop-and-test) · [Roadmap](#ordered-roadmap) · [Architecture](#core-architecture) · [Meet Weaver](https://github.com/masssi164/weaver)

> **In development:** Weave is not a finished production collaboration platform. `dev` is the current implementation lane; `main` still represents the older architecture line.

## What Weave is

A collaboration platform should let you choose its building blocks, not require an all-or-nothing stack.

**Choose providers by capability.** Files, Calendar, and Chat are product concepts, not vendor names. Replaceable adapters connect the services behind them. Provider-neutral does not mean every provider is already supported.

**Keep the context, not just an export.** A file, an event, and a conversation can belong to the same Space. Stable `weave://` references and cross-domain relationships are central to the design. The separate [provider portability epic #1498](https://github.com/masssi164/weave/issues/1498) requires Files adoption and replacement to preserve data, stable references, and effective permissions. Activation must stop if a required source property or permission cannot be preserved.

**Build the workspace you need.** The broader vision includes documents, tasks, and meetings as modular capabilities. The immediate implementation work concentrates on the Files, Calendar, and Chat foundation and its integration boundaries. The wider vision is not a list of already shipped features.

### Weave and Weaver

**Weave provides the collaboration foundation. [Weaver](https://github.com/masssi164/weaver) is its optional personal-assistant runtime.**

Weaver is an upstream-first OpenClaw distribution for a personal agent assigned to an entitled member. It is intended to work within organization-approved capabilities and current domain permissions — not become a second identity system or an unrestricted shortcut to provider credentials.

Weave does not require an agent to be useful. Weaver adds another way to work with it. Matrix supplies the conversational channel; workload-scoped MCP is a separately gated integration. **Managed MCP is currently disabled in Weaver**, and Chat is not duplicated as MCP tools.

## Current status

This is an existing codebase with substantial implementation, tests, and infrastructure, undergoing architectural consolidation. A passing component test or build is not evidence that every planned integration is ready for daily use.

The approved [product consolidation epic #1470](https://github.com/masssi164/weave/issues/1470) and [pinned specification policy](docs/specification-source-of-truth.md) define the current standalone-product delivery contract. Its stories are #1471–#1476 and #1479–#1481; #1477–#1478 belong to [provider portability epic #1498](https://github.com/masssi164/weave/issues/1498). #1470 acceptance requires integrated user, admin, MCP, Matrix, Files, and Calendar journeys, not compilation alone. Provider adoption, migration, cutover, and rollback are #1498 acceptance. Older Core and gateway work remains historical or reusable only as classified in the epics.

Weave does not claim public production readiness, unrestricted autonomous agents or universal provider interchangeability. The [product trust claim matrix](docs/product-trust-provider-choice-claim-matrix.md) records the scope and limitations of earlier evidence.

The portability promise is no unaccounted data loss; perfect lossless migration is not claimed as delivered. #1498 requires activation to remain blocked whenever required data or effective permissions cannot be preserved.

## Develop and test

Start from the implementation lane:

```bash
git clone --branch dev https://github.com/masssi164/weave.git
cd weave
python3 tools/core_docs_check.py
```

Use **Java 21** for the foundation gates below. Container tooling is needed for checks that actually start PostgreSQL, IAM, protocol, or full-system infrastructure. Run the checks relevant to your change; these are developer commands, not a production installer.

```bash
./gradlew specCorpusConformance
./gradlew acceptanceContract
./gradlew clientCi serverCi adminCi
./gradlew infraStatic docsCheck
```

Follow the [developer handbook](docs/developer-handbook.md) and [Gitflow workflow](docs/gitflow-pr-workflow.md) for prerequisites and protected promotion. Run focused checks for the area you change; full-stack checks require the corresponding local services.

Flutter, Node, Xcode, TestFlight, and manual release evidence are not prerequisites for unrelated Server/Data/MCP work.

Server controllers, transport models and validation generate the separate User and Admin OpenAPI artifacts. After changing that code, regenerate the consumers and verify the complete generation chain:

```bash
./gradlew generateOpenApiContract
./gradlew generateClientUserApi generateAdminApiClient generateAdminPublicUserApiClient
./gradlew generatedApiCi
```

Do not edit generated artifacts manually. Flutter consumes the User API; the Admin UI uses the generated Admin client for authorized operations and a separate generated User client for public pre-login configuration. MCP Files tools use the generated JVM User client, and product E2E uses generated User and separately credentialed Admin clients.
Remaining consumer and integrated acceptance work is tracked in the linked consolidation stories.

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

The current release boundary is a server-owned code-first User/Admin API with generated consumers, a Weave Matrix Client-Server northbound facade for Weave-owned Flutter and Weaver/OpenClaw clients, and provider-neutral Files and Calendar operations. Rust/Ruma/JNI handles Matrix wire parsing and projection; the Weave Chat domain owns canonical persistence, current authorization and southbound routing through `ChatProviderPort`. Exactly one provider is active per organization and module. One member-facing Weave OIDC/PKCE login makes authorized capabilities ready; Flutter uses that member session at the Weave Matrix facade. Chat E2EE is governed by organization and room policy, with client-owned cryptographic device state for encrypted rooms. Independent third-party Matrix-client interoperability and its separate OAuth profile, public northbound WebDAV/CalDAV, private Runners, Calls, and broad orchestration are outside this release. Detailed contracts belong to the [pinned specification policy](docs/specification-source-of-truth.md), [Matrix support profile](docs/reference/matrix-client-server-support-profile.md), and [epic #1470](https://github.com/masssi164/weave/issues/1470).

## Ordered roadmap

[Epic #1470](https://github.com/masssi164/weave/issues/1470) owns the standalone product: scope alignment (#1471), the code-first API and generated consumers (#1472–#1473), identity and authorization (#1474), Matrix interoperability (#1475), durable resources and one active binding (#1476), product flows (#1479), integrated E2E (#1480), and mainline closure (#1481). [Epic #1498](https://github.com/masssi164/weave/issues/1498) owns provider adoption and migration, including Files profiles (#1477), cutover, recovery, and rollback (#1478). Delivery status is tracked by those issues and protected branch checks; a roadmap entry is not a shipped capability.

## Documentation

- **Start contributing:** [Developer handbook](docs/developer-handbook.md), [Gitflow workflow](docs/gitflow-pr-workflow.md), and [contribution guide](CONTRIBUTING.md).
- **Read implementation notes:** [Data-sovereignty core](docs/architecture/data-sovereignty-core.md) and [package boundaries](docs/architecture/core-package-boundaries.md). The [transfer-kernel inventory](docs/architecture/canonical-transfer-kernel.md) records historical design; apply the pinned release profile to current work.
- **Check the evidence:** [Current workflow ownership](docs/development/current-workflow-ownership.md) and [CI runs](https://github.com/masssi164/weave/actions); use the historical [workflow disposition](docs/development/workflow-disposition.md) and [documentation audit](docs/documentation-audit.md) when tracing older decisions.

## Release notes

Earlier release notes describe their own historical candidates. Current #1470 capabilities require the linked implementation and acceptance evidence; the draft below is not proof of a completed standalone product.

<!-- WEAVE_RELEASE_NOTES_START -->
- Historical change draft: [Unreleased](docs/release-notes/unreleased.md)
- Release-note history and process: [Release notes](docs/release-notes/index.md)
<!-- WEAVE_RELEASE_NOTES_END -->

## Release evidence

Final delivery requires the accepted product journeys, exact remote `main` commits and applicable post-merge checks. Generated clients, local fixtures and earlier sprint reports each prove only their stated scope. See [delivery story #1481](https://github.com/masssi164/weave/issues/1481) for the remaining closure requirements.

<!-- WEAVE_RELEASE_NOTES:START -->
- Offline release-note fixture review artifact: `build/release-notes/unreleased.md`
- README pointer check: deterministic structure and generated-link validation; it does not establish product acceptance or release readiness.
<!-- WEAVE_RELEASE_NOTES:END -->

## Get involved

Bring a concrete collaboration problem, a reproducible issue, an interoperability test, or a focused contribution. Start with [the issues](https://github.com/masssi164/weave/issues) and agree on scope before a large change. Interface design, documentation, and installation feedback matter alongside code.

Weave is an independent project by Massimo. AI-assisted development and communication are welcome, with human responsibility, review, and disclosure.

## License

Copyright © 2026 Massimo (GitHub: masssi164).

Weave-authored code and documentation are licensed under **EUPL-1.2-or-later**, except where existing notices or documented third-party exceptions specify otherwise. See [LICENSE](LICENSE), [Notices](NOTICE.md), and [Third-party notices](THIRD_PARTY_NOTICES.md).

The vendored Matrix SDK crypto code and its upstream-oriented patch series retain their Apache-2.0 licensing and existing file-level notices. They are not relabelled as EUPL. Contributors retain copyright and follow the applicable licence and [DCO sign-off policy](CONTRIBUTING.md).
