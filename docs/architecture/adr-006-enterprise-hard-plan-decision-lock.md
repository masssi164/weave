# ADR-006: Enterprise hard-plan restructuring decision lock

Status: accepted

Evidence markers:

- ENTERPRISE_TARGET_DECISION_LOCK
- ENTERPRISE_TARGET_BOUNDARY_GATE
- ENTERPRISE_TARGET_E2E_SPINE

## Context

Massimo provided an architecture package for a full Weave restructuring on
2026-07-07. The package was inspected before this decision lock was written.
It contains a target architecture, migration roadmap, ADR drafts, Cucumber
features, CI gate sketches, SQL migration examples, and Java examples for
canonical persistence, open-standard protocol projections, provider switching,
MCP policy, Matrix gateway strategy, and the governed Weaver boundary.

The current implementation truth at intake was:

- `dev` head `869facc6694bd0ba1ae6238988e0a550a453fb0b`.
- No open pull requests.
- No open release blockers.
- Latest `dev` CI run `28738106247` passed.
- The pinned Weave Specification Corpus remains the product/domain source of
  truth through `specs/weave-specs.lock.json`.

This ADR does not import the package as canonical product truth. It records how
the package enters the Weave delivery system: as a restructuring input that must
be reconciled against the pinned corpus, existing ADRs, GitHub issues, and
executable gates.

## Decision

We will treat the hard-plan package as an active restructuring lane with a
decision-first issue DAG.

The accepted direction is:

- Weave keeps provider-neutral domain facades as product truth.
- Open standards are northbound projections or southbound adapter seams, not raw
  provider pass-throughs.
- Provider schemas remain adapter I/O. Canonical Weave entities are handwritten
  from Weave domain contracts.
- Durable persistence must move toward a relational baseline with migration and
  recovery evidence, while current JSON/file stores remain until parity and
  rollback are proven.
- Server package/module boundaries must become enforceable before a physical
  Gradle module split.
- Provider switching must fail closed unless canonical invariants and
  support-safe no-drift evidence exist.
- Files WebDAV remains the first accepted standard data-plane projection.
- OpenAPI remains the generated control/model contract until each domain has an
  explicit standard-protocol replacement gate.
- MCP tools remain semantic Weave tools governed by server policy, approval,
  redaction, and audit.
- Weaver remains an optional governed runtime integration, not a Weave product
  domain.
- Matrix client-server or federation claims require a separate Chat decision and
  conformance proof; current Chat API first guidance is not silently replaced.

The target package and module map is:

| Target area | Responsibility | Transitional repo location |
| --- | --- | --- |
| Domain kernel | Canonical domain entities, value objects, invariants, and use-case interfaces. | `server/src/main/java/com/massimotter/weave/backend/**/domain` |
| Application/use cases | Product workflows over domain ports; no direct provider or projection calls. | Current `service`/`domainfacade` code until moved slice by slice. |
| Projections | JSON control plane, WebDAV, CalDAV/CardDAV or iCalendar, Matrix, and MCP northbound adapters. | Current controllers and MCP modules until projection packages exist. |
| Persistence | Flyway/JPA-backed repositories and append-only ledgers behind domain ports. | Current JSON/file repositories until #1012/#1019 parity proof. |
| Provider adapters | Southbound Keycloak, Matrix, Nextcloud, OpenProject, LiveKit, and future adapters. | Current provider-specific packages until #1013/#1024 gates pass. |
| Policy, audit, credentials | Authorization, approvals, redaction, audit, credential references, and support-safe evidence. | Current `audit`, `context/authz`, identity, and readiness services. |
| Boot wiring | Spring composition root, configuration, and profile-specific adapters. | Current server boot module. |

## Delivery DAG

The restructuring lane is split into reviewable slices:

1. Decision lock and issue DAG (#1011).
2. Persistence foundation and recovery proof (#1012).
3. Module shell and architecture gates (#1013).
4. Provider switch no-drift conformance kit (#1014).
5. OpenAPI demotion and health simplification (#1015).
6. MCP Spring AI projection and Weaver boundary (#1016).
7. Matrix gateway canonical ledger and federation strategy (#1017).
8. Flutter and native client protocol boundary (#1018).
9. Target architecture E2E feature and evidence spine (#1025).

Retirement children close the loop after parity evidence:

- Strategic file/JSON mutable state retirement (#1019).
- OpenAPI authority and data-plane retirement (#1020).
- Python/OpenAPI MCP and long-term catalog-truth retirement (#1021).
- Chat API-first shell retirement (#1022).
- Dedicated Weaver channel/runtime abstraction retirement (#1023).
- Broad server package drift retirement (#1024).

Existing work remains part of the graph rather than being duplicated:

- #1002 standard-protocol facade follow-up tracker.
- #1004 server domain facades into ports/adapters.
- #1005 standard-protocol dependency/license matrix.
- #1006 Chat northbound standard projection strategy.
- #1007 Files WebDAV write policy.
- #969 Files native/WebDAV facade path.
- #967 Calendar native setup/sync facades.
- #968 Calls/Meetings provider-neutral app shell.
- #865 and #870 transitional Python/OpenAPI MCP work.
- #971 governed Weaver member surface.

## Consequences

- New implementation slices must cite the governing corpus file, repo doc, ADR,
  issue, acceptance scenario, and smallest meaningful gate.
- Direct imports from the package examples are not allowed unless they are first
  reconciled with repo conventions, dependency policy, and the pinned corpus.
- The first persistence work must be additive and profile-gated. It must not
  delete JSON/file stores until parity, restart/recovery, and rollback behavior
  are proven.
- The first module work must add enforceable dependency checks before broad
  package moves or physical Gradle module splits.
- Provider switch claims must be scoped to named providers and fixtures until
  broader conformance evidence exists.
- OpenAPI cannot be removed as a generated consumer contract. Domain data-plane
  demotion requires executable standard-protocol projection evidence plus
  OpenAPI control/discovery/revoke/status coverage.
- Matrix federation and E2EE remain high-risk areas. They require explicit
  identity, moderation, tenant-isolation, history, and supportability gates.

## Evidence gates

Minimum gates for this decision-lock and evidence-spine slice:

- `./gradlew --no-daemon docsCheck --console=plain`
- `./gradlew --no-daemon acceptanceContract --console=plain`
- `./gradlew --no-daemon :server:test --tests com.massimotter.weave.backend.architecture.ServerArchitectureBoundaryTest --console=plain`
- `./gradlew --no-daemon serverCi --console=plain` when the architecture test or server test wiring changes.

Minimum gates for implementation slices:

- `./gradlew --no-daemon specCorpusConformance --console=plain`
- `./gradlew --no-daemon acceptanceContract --console=plain` when acceptance or
  Gherkin mappings change.
- The smallest relevant area gate, such as `serverCi`, `clientCi`, `mcpCi`,
  `infraStatic`, or focused tests.

## Non-goals

- No mass refactor in this ADR.
- No live infrastructure mutation.
- No release readiness claim.
- No deletion of transitional stores, contracts, or MCP bridges.
- No public disclosure of private operator artifacts or local evidence paths.
