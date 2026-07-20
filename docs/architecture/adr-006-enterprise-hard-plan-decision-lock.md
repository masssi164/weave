# ADR-006: Enterprise hard-plan restructuring decision lock

Status: accepted

Evidence markers:

- ENTERPRISE_TARGET_DECISION_LOCK
- ENTERPRISE_TARGET_OPEN_STANDARD_NORTHBOUND
- ENTERPRISE_TARGET_OPENAPI_CONTROL_PLANE_ONLY
- ENTERPRISE_TARGET_NO_TRANSITIONAL_COMPATIBILITY
- ENTERPRISE_TARGET_BOUNDARY_GATE
- ENTERPRISE_TARGET_E2E_SPINE

## Context

Massimo provided an architecture package for a full Weave restructuring on
2026-07-07. The package was inspected before this decision lock was written.
It contains a target architecture, migration roadmap, ADR drafts, Cucumber
features, CI gate sketches, SQL migration examples, and Java examples for
canonical persistence, open-standard protocol projections, provider switching,
workload-only MCP policy, Matrix gateway strategy, and the Agent Runtime Control boundary.

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

Massimo's 2026-07-07 correction locks the implementation posture: this is not a
legacy-preservation or compatibility-layer program. The hard-plan target is the
implementation target. Historical JSON/file stores, OpenAPI data planes, and
provider-shaped member/native/MCP/mobile surfaces are not compatibility
contracts. They are deleted, blocked, or fenced as fixtures/import-only evidence
as soon as the matching target projection exists.

## Decision

We will treat the hard-plan package as an active restructuring lane with a
direct target-implementation issue DAG.

The accepted direction is:

- Weave becomes an OIDC/OAuth2-governed Open Standards Gateway with a canonical domain
  kernel.
- Files uses the Weave-owned `/dav/files` WebDAV facade as the durable file data
  plane.
- Calendar uses Weave-owned CalDAV/iCalendar plus setup, status, and revoke
  boundaries.
- The canonical People domain exposes Weave-owned Contacts/CardDAV/vCard plus
  setup, status, and revoke boundaries.
- Chat targets Weave-owned Matrix Client-Server core first, with federation
  identity only after identity, signing, moderation, retention, E2EE, and
  supportability gates exist.
- Calls use Matrix v1.19 plus pinned MatrixRTC Profile 0 as the only member
  signaling contract, with WebRTC media and an internal RTC Authorizer that
  independently validates current context before issuing short-lived SFU tokens.
  There is no member Calls REST API. LiveKit is the first SFU candidate; Teams/Slack
  meetings remain southbound link/meeting adapters.
- MCP uses Spring AI semantic Weave tools over domain use cases, not OpenAPI
  route mirrors. Weaver is a governed client/host/agent runtime integration
  boundary, not a canonical Weave product domain.
- Flutter is the Weave product client. It consumes Weave projections and control
  contracts, never provider SDKs or provider-native APIs.
- Open standards are northbound projections or southbound adapter seams, not raw
  provider pass-throughs.
- Provider schemas remain adapter I/O. Canonical Weave entities are handwritten
  from Weave domain contracts.
- Durable persistence and append-only ledgers own identifiers, policy, grants,
  audit, capability state, migration/switch evidence, and federation identity.
- Strategic JSON/file runtime truth is not preserved as the target. Existing
  file-backed defaults remain current implementation debt owned by #1019, not a
  compatibility contract for new slices.
- Server package/module boundaries must become enforceable before a physical
  Gradle module split.
- Provider switching must fail closed unless canonical invariants and
  support-safe no-drift evidence exist.
- OpenAPI is demoted to control, admin, setup, revoke, manifest, and generated
  convenience surfaces. It is not product/data-plane authority.
- Southbound providers exist only behind ports/adapters.

The target package and module map is:

| Target area | Responsibility | Transitional repo location |
| --- | --- | --- |
| Domain kernel | Canonical domain entities, value objects, invariants, and use-case interfaces. | `server/src/main/java/com/massimotter/weave/backend/**/domain` |
| Application/use cases | Product workflows over domain ports; no direct provider or projection calls. | Current `service`/`domainfacade` code only until replaced slice by slice. |
| Projections | OpenAPI control/admin/setup/revoke/manifest convenience, WebDAV, CalDAV/iCalendar, People-domain CardDAV/vCard, Matrix Client-Server, and Spring AI MCP northbound adapters. | Current controllers and MCP modules only until projection packages exist. |
| Persistence | Flyway/JPA-backed repositories and append-only ledgers behind domain ports. | Current JSON/file repositories only as #1019 retirement debt, temporary import, dev, or fixture-fenced evidence until strategic runtime truth is removed. |
| Provider adapters | Southbound Keycloak, Matrix, Nextcloud, OpenProject, LiveKit, directory/contact, and future adapters. | Current provider-specific packages until #1013/#1024 gates pass. |
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
9. People/Contacts CardDAV facade and native address-book boundary (#1031).
10. Target architecture E2E feature and evidence spine (#1025).

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
- #1031 People/Contacts CardDAV facade and native address-book boundary.
- #968 Calls/Meetings provider-neutral app shell.
- #865 and #870 transitional Python/OpenAPI MCP work.
- #971 historical Weaver member-surface proposal, superseded by admin/operator Agent Runtime Control lifecycle and Matrix-channel runtime interaction.

## Consequences

- New implementation slices must cite the governing corpus file, repo doc, ADR,
  issue, acceptance scenario, and smallest meaningful gate.
- Direct imports from the package examples are not allowed unless they are first
  reconciled with repo conventions, dependency policy, and the pinned corpus.
- The first persistence work may be profile-gated for evidence and ADR-007 keeps
  some file-backed defaults until #1019. Those defaults are current retirement
  debt, not strategic compatibility. Production/dogfood target profiles must
  move away from JSON/file runtime stores, with one-shot import evidence only
  when real dogfood data requires it.
- The first module work must add enforceable dependency checks before broad
  package moves or physical Gradle module splits.
- Provider switch claims must be scoped to named providers and fixtures until
  broader conformance evidence exists.
- OpenAPI remains useful as generated control/admin/setup/revoke/manifest
  convenience, but every retained OpenAPI endpoint must be classified. Product
  data-plane authority moves to the relevant standard projection or domain
  kernel use case as soon as that projection exists.
- Matrix Client-Server core is the chat target. Federation and E2EE remain
  high-risk areas and require explicit identity, moderation, tenant-isolation,
  history, signing, retention, and supportability gates before readiness claims.
- Provider-switch/no-drift work is supporting evidence for provider isolation,
  not the lead architecture slice.

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
- No broad compatibility layer to preserve historical JSON/file, OpenAPI
  data-plane, route-scraped MCP, or provider-shaped member/native behavior.
- No public disclosure of private operator artifacts or local evidence paths.
