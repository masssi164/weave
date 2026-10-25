# Server ownership and domain consolidation

Implementation owner: #1745; integration parent: #1741; PR: #1746. This is the current implementation guide, not specification-corpus authority or release qualification.

## Current state versus target

**Implemented:** Files and Calendar own their actual serving runtimes: authenticated services, controllers, wire
values, provider integrations, settings, resources and tests, plus pure model and transactional
JPA adapters. Their owning checks and real Boot discovery run without App or sibling domains. Calendar owns
its temporal/recurrence orchestration, native/CalDAV providers, pure model, normalized mappings
and production JPA payload store. Core owns shared persistence
under actual identity/security/Spaces/audit/operation/binding/readiness/schema packages; its pure
model has no optional feature implementation. Chat, Agent Runtime and Portability have owning
JPA libraries; Agent Runtime and Portability models and existing adapter libraries are below
Server. All former Server-owned root projects are retired. Five shadowed adapter copies were
removed without weakening the active security behavior.

Core and the five persistence owners register scoped entity/repository wiring through their
own Boot imports resources. Six actual Boot discovery tests pass. Shared provider health uses
domain contributions, and shared member/session revocation no longer imports Chat types.
The health cache, safe response, deferred probe contract and typed settings now live in
Core health. Its module-local servlet auto-configuration is discovered without optional
domain classes and does not probe providers during construction. The global provider
configuration is retired; Files, Calendar, Chat, Boards, Integrations and Meetings own
their existing registry metadata and adapter selection.

**Executable composition:** `server/app` is now the only Boot executable; `server` is a base
project that resolves domain selection once. Root tasks, API/schema tooling, host runners,
Docker JAR paths and complete source inputs target App. The independent nested Server
wrapper/settings were retired in favor of the repository-pinned root wrapper.

**Still transitional:** Chat services/controllers/providers, remaining shared platform
services and future-domain runtimes still require extraction. Real selected-runtime packaging, future-domain
release exclusion and reduced discovery/client integration are not complete. Chat retains a verification bridge; Files and Calendar use genuine owning checks. Partial App packaging/API/schema export guards remain
until real selected runtime composition is implemented. No ARM64,
native or live/human release qualification is claimed from library checks.

The initial release product scope is **Files, Calendar and Chat**. Boards, Office, DevOps, Agent Runtime, portability/transfer, guest-facing and integration features remain planned domains. Move their implementations out of Core rather than deleting agreed product scope.

## Ownership rules

| Owner | Responsibility |
| --- | --- |
| App | Executable entry point, composition of selected domains, OpenAPI export and the complete historical Flyway catalog |
| Core | Genuinely shared identity, organization admission, Security, Spaces, audit and narrow cross-domain contracts |
| Domain model | Provider-neutral values and invariants; no Spring, JPA, HTTP, Jackson or provider SDK |
| Domain persistence | That domain's entities, IDs, repositories, transaction adapters and persistence-specific dependencies |
| Domain application/API | Authenticated use cases, orchestration, controllers and wire DTOs |
| Domain provider integration | Provider adapters, their configuration, transport/serialization and SDK dependencies |

There is no general requirement for catch-all `core/adapters` or `core/persistence` projects. Shared Security/Spaces/Identity may need persistence, but it belongs with that actual platform responsibility. Files or Calendar entities never become Core merely because they use the same database.

Core must not depend on an optional domain implementation. Domains must not depend on the executable App or another domain's repositories. A source move that needs a reverse dependency is not complete; extract a small honest contract instead of hiding a cycle.

**Security exception:** the historically Matrix-named durable member/session revocation storage is shared authentication state. It must remain available without Chat, preserve its existing table/hash format and fail closed. Room/device/crypto state belongs to Chat.

## Current leaf projects

- `:server:domains:files` runtime, `:server:domains:files:model` and `:server:domains:files:persistence`: see `server/domains/files/README.md` for ownership and removal rationale.
- `:server:domains:calendar` runtime, `:server:domains:calendar:model` and `:server:domains:calendar:persistence`: see `server/domains/calendar/README.md`.
- `:server:core:model` and `:server:core`: shared pure contracts and responsibility-owned persistence.
- Agent Runtime `model`, `persistence`, `provider` and `security`; Portability `model` and
  `persistence`; Chat `persistence`: see their owning READMEs.
- Shared Java/toolchain/JUnit mechanics: `server/gradle/scripts/domain-library.gradle`. Dependencies remain in each library. Domain-qualified groups and archives prevent sibling libraries named `model` or `persistence` from claiming the same Gradle component identity.

The canonical package-direction rules remain in `docs/architecture/core-package-boundaries.md`. Keep model/provider/persistence/API boundaries explicit; do not generate a Gradle project for every package or maintain empty placeholder domains.

## Domain runtime composition

Each completed runtime domain registers its own `@AutoConfiguration` class through:

```text
src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

Use one fully qualified class name per line, explicit imports and no broad domain component scan. App does not maintain a second handwritten list of every domain bean. Pure Java model libraries need no fake Spring registration. Serving beans must not accidentally load in schema-init or Dogfood operator CLI contexts.

Optional shared workflows consume domain-owned contributions through small contracts, not concrete Files/Calendar/Chat implementations. Domain inclusion is a build decision; provider binding is a separate runtime decision.

## Build selection and OpenAPI

The agreed selection rule is explicit pipeline `domains` / `-Pdomains`, then a matching branch's `pinned_domains`, then `all`. `all` expands the canonical selectable catalog. The current bridge still catalogs only Files,
Chat and Calendar; catalog expansion to implemented future runtimes is part of unfinished
production composition, not permission to redefine final `all` as the release set. Reuse the shared resolver from #1744 when integrating production composition; do not invent another resolver or silently package excluded code. Branch pins are resolved to exact source commits. Domain set, platform, toolchain/flavor, source and spec identity must participate in artifact identity and digest consumption.

The selected controllers and their Spring handler mappings must exist **before** code-first OpenAPI export. Use actual Gradle prerequisite dependencies, not ordering hints alone. Generated User/Admin clients follow the completed export. Keep the accepted OrgManifest/API contract intact; reduced/no-Chat discovery requires its explicit accepted contract change.

Automatic delivery target remains the agreed Linux ARM64 container for Apple Silicon. Other platform variants are manual. Native Image/AOT, JNI and actual consumption on the Mac mini require separate execution evidence; moving Java classes is not that evidence.

## Cleanup and code style

Delete verified duplicates and obsolete build plumbing; do not leave commented-out old implementations. Every removal records the old location, replacement (or why none is needed), compatibility consequence and reason in the commit and the owning domain README.

Do not delete planned features, retained database state or historical migrations because they are outside the first release. Preserve behavior while moving, then simplify deliberate code paths in separately reviewable changes.

Prefer immutable records, constructor injection, typed configuration, declarative HTTP interfaces and explicit Spring auto-configuration. Use Lombok only for real boilerplate; do not replace invariants or add implicit credential `toString()` / JPA equality semantics. Use streams where clearer, not to disguise stateful transaction or lock ordering.

## Verification and maintenance

From the repository root:

```sh
./gradlew :server:domains:files:check :server:domains:calendar:check
./gradlew coreArchitectureCi
```

These are library checks, not a claim that the production domain selector is finished. Existing complete Server, PostgreSQL, API and protocol checks remain required. The source-level relational inventory includes nested domain projects, not only the old root locations; domain architecture tests inspect their compiled classes. Image source-input closure includes new module sources/build files and shared build mechanics.

The active historical Flyway catalog remains unchanged during extraction. Entity-first
authoring and any deliberate pre-release consolidation follow the explicit corpus proposal
and integrity/recovery gates; source moves never reset data or rewrite applied history.

## Living six-phase execution plan

Primary classification: `cross-repo-contract`. Implementation owner: `weave`; normative owner:
`weave-specs`. Baseline: PR #1746 at `7440a32e45`; accepted corpus
`f049c0b1879414eb529a3df83a18224407f1a436`. Continue the existing branch, preserve concurrent
changes and merge upstream normally. The shared resolver from PR #1744 is integrated;
its Files model task now uses the extracted coordinate. The temporary source archive
workflow is removed as its own instructions required, resolving both current CI failures.

| Phase | Milestone and implementation decisions | Checks | Status / blockers |
| --- | --- | --- | --- |
| 1. Ownership and contracts | Inventory source/resources/dependencies and assign actual owners; prepare Entity-first and selected-domain discovery corpus changes; retain this plan as the single execution record | Corpus lint/examples, resolver tests, source inspection | In progress; accepted pin remains unchanged pending normal corpus review |
| 2. Domain composition | App is the sole executable; Core owns justified platform responsibilities; runtime modules own imports registration and scoped persistence; separate non-web tooling | Compile, dependency graph, real imports discovery and bean uniqueness | App executable split, Core shared wiring and actual Files/Calendar runtime/imports implemented; Chat and future runtime extraction pending |
| 3. Vertical extraction and ORM | Files/Calendar/Chat own services/controllers/providers/persistence; future domains remain buildable outside release; convert ordinary Calendar/Matrix JDBC CRUD | Domain units, PostgreSQL writes/isolation/constraints/transactions, no legacy copies | Files/Calendar vertical runtimes and Calendar/Matrix ORM extraction implemented; remaining Chat/future runtime/API ownership open |
| 4. Core and build finalization | Remove feature dependencies from Core; one selection drives real checks, runtime, schema/API and identity; preserve data-safe initializer until qualified cutover | Architecture rules, selected JAR/SDK inspection, offline deterministic schema | Shared session/identity/policy and health implementations extracted into Core; runtime selection remains pending; schema cutover requires accepted corpus and integrity/recovery evidence |
| 5. Runtime and clients | Release and three single-domain variants start with honest capabilities/endpoints; generated User/Admin contracts follow registered handlers; Flutter uses stable destinations | API/auth/client freshness, variants, Compose/two-member acceptance, ARM64/AOT/JNI where available | Pending; no live or native qualification claimed |
| 6. Review and delivery | Consolidate docs/Skills/instructions; review combined diff; coherent commits/push and exact-revision PR evidence | Applicable combined CI, diff review, protected checks | Pending; no protected merge/deployment/data reset authorized |

### Ownership map and removal rationale

| Current location / owner | Target owner / action | Rationale and dependencies |
| --- | --- | --- |
| Server executable, operator dispatch and web composition | App; move | Composition and application entry points, not feature business logic |
| Files model/JPA libraries and Server Files controllers/use cases/providers | Files; retain libraries and move runtime | Files owns storage authority, DAV/OpenDAL/S3, bindings and mutation semantics; uses Core authorization/Spaces/audit |
| Calendar model/JPA libraries and Server Calendar runtime | Calendar; retain libraries and move runtime | Calendar owns temporal/recurrence rules, native/CalDAV access and scoped persistence; uses Core authorization/Spaces |
| Server Chat/Matrix, canonical Chat JPA | Chat; move | Chat owns facade/protocol/sync/device/crypto, Synapse and JNI; uses Core security and stable Space identifiers |
| Historically Matrix-named session revocation | Core Security; retain/move | Shared authentication must work without Chat; retain storage/hash semantics |
| Root application-core Agent Runtime model and root runtime adapters | Agent Runtime; move | Deferred feature, not shared platform or release prerequisite; retain implemented tests |
| Root application-core transfer and Server portability/migration | Portability; consolidate | Same transfer/report/reconciliation responsibility; shared binding contracts remain Core |
| Server Boards, Office, DevOps | Separate future domains; move | Their use cases/providers/SDKs must not enter the release transitively |
| Server connector/interoperability and guest features | Integrations and Guest Access; move | Retain implemented behavior without production-ready claims or duplicated membership/security |
| Root generic JPA project | Owning domains or Core responsibilities; split/delete | PostgreSQL is not a business owner; Identity/Security/Spaces/audit remain independently useful |
| Global provider configuration and shared services importing features | Domain-owned contributions plus narrow Core contracts; split | Avoid reverse dependencies, fake readiness and required optional-domain beans |
| Generated API clients, MCP, product E2E | Retain standalone projects | Independent public consumers/process/verification boundary; not Server-internal modules |

The initial inventory counted 795 Java source types before the runtime moves.
Move resources, source guards, build inputs and tests with their consumers. Verify call sites,
bean registration, resource/reflection and native loading before removal. No broad source-set
redirects, forwarding controllers or duplicate production copies are acceptable.

### Validation record

- Initial remote inspection: current Dev is an ancestor of #1746; default checkout is clean on
  an unrelated Files branch and is preserved. Work continues in an isolated checkout.
- Current PR CI: API export, architecture, PostgreSQL and protocol foundation succeeded;
  aggregate CI failed on the temporary workflow's disposition and action pin comments.
  These previous checks do not qualify a changed candidate or selected-runtime composition.
- Local tools observed: Java 21, Docker and Flutter available. Use repository-pinned versions;
  local tool presence is not a test result.
- Required final commands: `./gradlew :server:domains:check` with omitted, `all` and
  `files,chat` selections, plus `:server:app:bootJar` with `files,chat,calendar` and `files`.
  Add real Calendar-only/Chat-only startup and artifact inspection, authoritative PostgreSQL,
  generated consumer, security, architecture and existing combined gates.

Update this record with exact commands/results as implementation proceeds. Missing external
acceptance or platform evidence remains a blocker; it never becomes an inferred pass.

### Calendar implementation checkpoint

- `NativeCalendarRelationalStore` now executes JPA over the owning normalized mappings and
  physically resides in Calendar persistence. Removed ordinary JDBC CRUD and unused duplicate
  recurrence projection; the active application recurrence engine remains.
- Native ranged reads query scoped candidate IDs before fetching payloads. Added empty-window
  and scoped replacement/delete regressions; no schema or provider behavior changed.
- Before the physical move: Calendar persistence 8/8; focused unit/architecture 8 passed with
  6 PostgreSQL-only skips; authoritative PostgreSQL 14/14; Calendar semantic/API units 65/65
  plus the existing 12 Cucumber Boards scenarios passed. PostgreSQL required an explicit
  observed local Docker socket via `DOCKER_HOST`; no persistent Docker configuration changed.
- After moving production store/unit test: `./gradlew :server:domains:calendar:persistence:test
  :server:app:compileTestJava --no-daemon --console=plain` passed.
- `python3 -m unittest tools.domain_selection_resolver_test tools.domain_unit_workflow_test
  tools.test_dev_ci_impact`: 66 passed. `:server:domains:check -Pdomains=files,chat` passed.
- Corpus proposal: [weave-specs#172](https://github.com/masssi164/weave-specs/pull/172). Its local
  lint (159 entries/26 example pairs), vector freshness, ready-claim self-test, link check and
  bundle build passed. It is proposed, not the implementation's accepted pin.

### Server-internal library consolidation checkpoint

The former root application-core project is split into Core model, Agent Runtime model and
Portability model. Root JPA sources are split into Core and Chat/Agent Runtime/Portability
persistence owners; framework-free source sets and compiled architecture tests remain separate.
Root provider/security adapters move under Agent Runtime with their existing security/provider
dependency tests. Generated clients, MCP and product E2E remain standalone. The old root build
projects are removed with no source-set forwarding.

This relocation does not yet extract the remaining executable's services/controllers or
exclude future runtime code from release assembly. Shared workload-security types historically
in Agent Runtime still need their actual Core security boundary before runtime exclusion.

Duplicate-source review found four additional shadowed Agent Runtime adapters. Their active
Server implementations replace stale root-library copies under the actual adapter owner.
In particular, the active exact-audience token policy and explicit READ_ONLY/READ_WRITE
SecretRef access modes are preserved; stale copies must not silently weaken them. Related
unit tests move into the adapter libraries. One whitespace-equivalent signer duplicate was
also removed. Source architecture inspection now walks every real Server production root.

### Shared security and persistence discovery checkpoint

- Shared session port and JPA adapter now belong to Core Security. JWT validation and member
  cutoff depend on shared security; Chat retains identity projection and delegates shared state.
  Existing table/hash, claim precedence, expiry, bounded retries and independent transactions
  are preserved. Focused auth/Matrix/security: 131 passed; authoritative PostgreSQL: 6 passed.
- Health contribution tests: 17 passed; Java reference checks: 13 passed; Python static checks:
  16 passed. Domain-owned configuration preserves existing provider routing and cached probe
  semantics; absent contributions create no entries/gauges and duplicate IDs are rejected.
- Six persistence projects now prove actual Boot imports-resource discovery and real repository
  construction without manual test imports of their configurations. These use H2 only for
  bean wiring; authoritative integrity and recovery evidence remains PostgreSQL.
- Artifact input closure follows moved production sources/build files.
  `TMPDIR=/private/tmp python3 tools/build_artifacts_test.py`: 39 passed; the canonical macOS
  temporary path avoids `/var` versus `/private/var` fixture mismatches without changing product code.
- Source inspection walks every real Server production root and reports no duplicate types or
  retired root projects. Physical `@Column(name="tenant_id")` metadata is distinguished from
  human-token claim aliases; the executable still cannot declare entities/repositories.
- Corpus proposal #172 hosted Specification conformance succeeded; normal review/acceptance
  and the resulting implementation pin update remain pending.

### Shared Space contribution checkpoint

Shared relationship listing now consumes installed Calendar/Files/Chat contributions. Each domain
owns bounded candidate selection, current access readback, ID validation and protocol projection.
Shared member/Space admission, event/file/room cursor ordering, pagination and support-safe
errors remain centralized. Omitted-domain cursors fail explicitly; missing domains do not
create synthetic results or unsatisfied dependencies.

Default Space preparation likewise consumes installed-domain guards. Files retains existing
capability/snapshot checks; Calendar captures/rechecks its binding. The exact order is shared
authorization, all snapshot checks, authority capture, unchanged membership primitive, authority
recheck and audit. Zero-domain and single-domain compositions preserve the shared primitive.

Focused service/contribution/controller/bootstrap checks: 48 passed; authoritative Space and
Dogfood preparation PostgreSQL checks: 13 passed. Existing incidental Cucumber discovery also
ran its 12 scenarios. These are bounded backend checks, not two-member native acceptance.

### App split checkpoint

- Moved the executable source sets and build into `server/app`; no source-set aliases or
  duplicate production copies. `server` retains only the shared resolver and composition
  mechanics. Domain library dependencies remain explicit and currently include future
  code until the remaining vertical extraction is complete. Partial packaging still fails.
- Updated root gates, API export, host runners, CI callers, Docker artifact paths, source
  guards and source-input closure. Retired `server/gradlew`, nested settings and duplicate
  wrapper distribution; historical executed-command evidence remains unchanged.
- Before this move, `coreArchitectureCi canonicalDataCi :server:test :server:postgresJpaTest
  specCorpusConformance` passed against the accepted corpus. Those historical task names
  describe that checked revision; current executable tasks use `:server:app:`.
- Review corrected incomplete Core Identity and Portability persistence discovery. Owning
  module checks passed all 8 tests without skips, asserting the specific formerly omitted
  entities and repository beans.

- App checkpoint validation (source represented by the App split commit):
  `DOCKER_HOST=unix:///Users/flotterotter/.orbstack/run/docker.sock` and
  `WEAVE_SPEC_CORPUS_ROOT=<accepted f049c0b18794 checkout>` with
  `./gradlew coreArchitectureCi canonicalDataCi serverCi specCorpusConformance
  checkOpenApiContractFresh --no-daemon --console=plain`: **passed**. App unit lane
  1,478 tests / 14 PostgreSQL-only skips; PostgreSQL lane 189 / zero skips; Core 13 /
  zero skips. Actual User/Admin export matched committed contracts.
- `./gradlew :server:app:bootJar -Pdomains=all --console=plain`: **passed**. This is
  the full current transitional composition, including future code, not the release
  runtime or a reduced variant. `ProviderRegistryConfigurationTest`/controller focus:
  36 passed, no skips. Selection/workflow/Gradle wiring: 13 passed. Source-guard,
  resolver and impact fixtures: 84 passed. Artifact identity/input fixtures: 39 passed
  (`TMPDIR=/private/tmp` avoids the macOS `/var` alias in fixture canonicalization).

### Shared Identity/Security extraction

Identity resolution/context and identity-key formatting now belong to Core Identity. Shared
Security owns Keycloak organization parsing/admission, session cutoffs/revocation and typed
settings. Foundational Space authorization values/ports are framework-free in Core model;
the configured policy adapter and strict seed loader remain Core runtime. The common HTTP
error contract lives in Core API errors. No generic Core service/model/config dumping ground
was created. Core has no optional domain dependency.

The actual serving services register through module-local servlet auto-configuration; schema
and operator contexts retain their explicit independent wiring. Existing default construction
consumers retain the same fail-closed configured-policy defaults. Human JWT fixtures moved
into Core test fixtures, with direct App test dependency rather than duplicate helpers.

- Shared Identity/Security checks: `./gradlew :server:core:check :server:core:model:check
  :server:app:test --tests '*JwtDecoderConfigTest' --tests '*FirstPartyIdentityContractTest'
  --tests '*AdminUserHttpAdmissionTest' --tests '*SpacesRelationshipsUserControllerTest'
  --tests '*DefaultSpaceBootstrapServiceTest' :server:app:javaAgentReferenceTest
  --console=plain`: **passed**. 102 focused App tests, 39 Core tests and 3 Core-model
  tests, zero failures/skips. Core discovery asserts actual shared identity/policy/session
  beans are singletons with no optional domain on its test classpath, and absent in the
  non-web persistence context.

### Matrix normalized persistence checkpoint

The last ordinary collaboration JDBC store moved from App to Chat persistence and now uses
typed JPA over 15 owning normalized mappings. The broad filename JDBC exemption is removed.
Only the exact PostgreSQL transaction advisory fence for absent aggregate rows is allowed;
negative architecture tests reject SQL/parameter changes, extra queries or copies elsewhere.
JPA module discovery separates serving-store construction from non-web metadata tooling.

The mapping graph retains the existing normalized tables, SQL-only integrity rules represented
by supported checks/partial indexes, default values and synchronization version semantics.
The generated schema remains a candidate: these source changes do not rewrite applied
migrations or reset data. Existing proof/backup PostgreSQL tests now exercise the JPA adapter.
New direct store regressions exercise tenant scope, bounded/locked claims, concurrent winners,
revisions/journals, progress, backups, rollback, and repeated delete/reinsert in one transaction.

- Final Chat ORM command with disposable PostgreSQL: `./gradlew
  :server:domains:chat:persistence:check :server:app:test
  --tests '*ServerArchitectureBoundaryTest' --tests '*MatrixE2eeStateService*'
  :server:app:postgresJpaTest --tests '*MatrixE2eeRelationalStorePostgresTest'
  --tests '*MatrixDeviceProofPostgresTest' --tests '*MatrixBackupVersionPostgresTest'
  --tests '*EntityFirstBaselinePostgresTest' --console=plain`: **passed**. Chat 5, App 45
  (25 architecture, 8 Matrix state, 12 existing Cucumber), PostgreSQL 18 (9 new store,
  6 proof/recovery, 1 backup concurrency, 2 Files/Calendar baseline), no failures/skips.
  Generated schema uniqueness/checks executed against PostgreSQL; previous timestamp
  projection and repeated-mutation failures were corrected and their regressions pass.

### Shared provider contracts and Audit ownership

Core Discovery owns shared provider status contracts and generic runtime-status construction;
Core Provider Binding owns the framework-free conformance/readiness values. Shared redaction
and HTTP Retry-After parsing belong to Core Security and HTTP respectively. The unused-by-serving
portability report mapping remains buildable in future Portability rather than becoming a
shared platform implementation. Domain-specific provider registry beans remain with domains.

Core model now owns the cross-domain audit envelope, publication contract, redaction and
required-publication rules. Core Audit persistence owns the real JPA sink, registered through
module-local serving auto-configuration. Operator preparation keeps its explicit sink. Future
connector/consent event builders are under Integrations, outside Core. Their existing tests
remain there; four shared envelope/privacy/publication tests moved to Core.

The old Boards fixture that directly called App's removed audit factory was retired: actual
Core Boot-import discovery now asserts the singleton JPA audit authority. The Boards-specific
no-persistence-fallback assertion remains. This strengthens ownership verification rather than
retaining an App factory solely for an obsolete direct-construction test.

### Release MCP ownership checkpoint

Files/Calendar consume `McpWorkloadAuthorization`; the actual release implementation,
protected binding reader, exchanged-token policy, current Keycloak entitlement, signed
administrative token provider and shared SecretRef contracts live in Core Security. The
future Agent Runtime implementation retains only cells/profiles/governance and future
person-directory resolution. Its old nullable release branch is removed. The shared
principal carries resolved binding/audit/policy metadata from its actual owner. Core
production has no Agent Runtime, Files, Calendar or Chat implementation imports.

Module-local servlet auto-configuration discovers release authorization without the future
runtime on the classpath; non-web tooling does not construct that authority. Existing
configuration keys/defaults and mutually exclusive release/future authorization modes are
preserved. No token shape, permissions, Keycloak authority, provider selector or new public
environment setting was introduced.

- Final owning unit/check lane: whole Core 75, Core model 3, App 190 (workload/JWT/
  Files/Calendar/Keycloak/config/architecture plus provider/audit/Boards regressions),
  future model 4/provider 5/security 23: **300 passed, no skips**. Exact class filters
  and logs are preserved in the associated commit/PR validation record.
- PostgreSQL: Runtime Cell 7/Command 3/Governance 3/Profile 6 and audit publisher 1: **20
  passed, no skips**, against disposable infrastructure. The separate existing audit
  PostgreSQL class also passed after moving it from ordinary unit execution into the
  registered PostgreSQL lane. No database data or migrations changed.

- Combined foundation validation: `./gradlew coreArchitectureCi canonicalDataCi
  checkOpenApiContractFresh specCorpusConformance --no-daemon --console=plain` with the
  accepted corpus: **passed**. Actual full App handlers booted/exported and User/Admin
  artifacts remained fresh. Current release workload libraries are independent of
  Agent Runtime; real selected App composition is still pending.
- Remaining transport cleanup includes suitable existing Keycloak REST integration under
  the shared declarative HTTP standard, preserving bounded bodies, authentication,
  allowlists, timeout and error behavior; no framework upgrade is authorized.

### Shared operation and provider-binding runtime

The actual `operation.adapter.JpaOperationIntentRepository` and
`providerbinding.adapter.JpaProviderBindingRepository` move from App into Core beside their
owning mappings. Module-local serving auto-configurations import the real adapters and
construct the shared operation service. Their scoped transactions, outbox atomicity,
idempotency reconciliation, active-binding locks and bounded mapping queries are preserved.
Non-web export/operator contexts retain their explicit graph.

Provider selection values/port/store exception move from App's generic `provider` package to
Core model `providerbinding.selection`; the typed JPA implementation belongs to Core
`providerbinding.persistence`. App no longer declares these shared beans. Provider selection
remains organization runtime state, independent of build-time domain selection.

Feature-specific replacement guards are extracted from the shared binding adapter into
Files, Calendar and Chat `domain` policies, explicitly registered by their owning configuration.
Files/Calendar still require identity carry-forward when mappings exist; Chat still requires
provider reconciliation for every replacement. Core rejects activation without an installed
policy, and duplicate policy identities fail construction. Domain bootstrap/use cases remain
with their features. These internal package/type changes do not change the public API or
accepted corpus pin; no provider migration functionality is added.

Verification for this checkpoint: `:server:core:check :server:core:model:check
:server:app:test --tests '*ProviderRegistry*Test' --tests '*AdminControlPlaneServiceTest'
--tests '*ServerArchitectureBoundaryTest'` passed (Core 75, model 3, App 70, zero skips).
`:server:app:postgresJpaTest --tests '*JpaProviderSelectionRepositoryTest'
--tests '*JpaProviderBindingRepositoryPostgresTest' --tests '*JpaOperationIntentRepositoryPostgresTest'
--tests '*FilesMutationIntentServicePostgresTest' --tests '*CalendarBindingBootstrapPersistenceTest'`
passed against disposable PostgreSQL (19 tests, zero skips). Java standard, provider boundary,
Gradle structure and documentation static checks pass. The additional missing/duplicate-policy
ownership cases passed in a subsequent Core check (77 tests total, zero skips). These checks prove this bounded
extraction; selected-domain packaging, full runtime extraction and release acceptance remain open.

### Core profile and device credential ownership

Product-profile values and storage ports move from App's generic `service` package to Core
model `identity.profile.domain` / `identity.profile.port`. The existing JPA adapter, use cases
and transport DTOs move into Core `identity.profile.persistence`, `application` and `api`.
The shared error envelope now belongs beside `api.error.ApiErrorException`. Core's servlet
imports register the actual profile repository/service, always using configured identity
resolution; production no longer supplies a constructor that silently creates default policy.
App's existing HTTP controller composition remains until its broad component scan is retired.
Serialized profile/error shapes and endpoint behavior remain unchanged.

Security owns device credential values, repository, adaptive hashing, expiry/revocation and
JPA implementation. Files and Calendar contribute their installed credential scopes; Core
contains no hardcoded feature list. Empty installation permits no scope, duplicate ownership
fails, and schema/non-web entry points do not construct serving credential services. Existing
PBKDF2 parameters, credential lifetime and authorization rules are preserved. Credential and
issuance records explicitly redact string output so logging cannot reveal a hash or issued
secret. Test installation helpers and the old file-profile fixture live only in Core test
artifacts, outside serving artifacts; owning profile/security unit tests no longer need App.

Remaining shared prerequisites for Files runtime include Space contribution contracts and
serving security/error adapters still under App. The current error handlers contain a Matrix
protocol branch that must become Chat-owned before Core can own the handlers. This is an
actual source dependency, not a reason to add a Core-to-Chat implementation dependency.

### Foundational Spaces and shared HTTP security

Core now owns the actual Space contribution contracts, API values (`spaces.api`), independent
user/relationship projections (`spaces.application`) and explicit serving wiring for its
membership/access/provisioning JPA adapters. Old component stereotypes are removed; App's
component scan is no longer their registration mechanism. The Dogfood adapter remains
explicitly imported by the non-web preparation graph. Default preparation and administrative
use cases still have pending Identity/runtime dependencies in App; feature-specific resource
checks remain domain contributions. The independent Space user unit test moves with its
implementation; cross-domain integration remains in the existing App lane.

Shared request correlation is in Core `http.request`; the error writer is in `api.error`, and
API authentication/access-denied adapters are in `security.http`. Their module-local serving
imports construct actual beans, with a narrow protocol error-envelope contribution. Chat owns
the concrete Matrix Client-Server denial writer and registration. Shared handlers have no
Matrix path/JSON knowledge. The same Keycloak token validation, authorities, safe request IDs,
HTTP statuses, Matrix error codes and JSON envelopes are preserved. Tests exercise shared
401/403 without optional protocols, actual Core imports discovery and the existing Matrix
filter/controller behavior. This is error transport ownership, not a new permission system.

Whole-App validation exposed HTTP slice fixtures that had relied on App scanning the moved
shared correlation/Matrix writers. Their explicit imports now install the real owning
components; assertions and authorization are retained. Eight existing PostgreSQL-only
Testcontainers classes lacked the tag already excluded by the ordinary unit task. They now
use `postgres`; the missing migration-evidence class is added to the existing PostgreSQL
lane filter. No test is deleted or weakened. The unit lane is rerun without Docker access,
and the real PostgreSQL lane remains required separately; unavailable Docker is not a pass.

### Workspace capability policy and installed declarations

Core now owns the actual shared `capability.WorkspaceCapabilityService`, shared typed shell/
administration settings, capability/readiness wire contracts and effective-policy DTOs.
`CoreWorkspaceCapabilityAutoConfiguration` registers serving policy and release readiness
through Core's imports resource. Non-web schema/operator discovery does not construct them;
the operator explicitly composes its required shared policy. Core requires no optional-domain
implementation or provider to construct this graph.

Domain-owned `*WorkspaceCapabilities` classes declare their capability keys, administrator/
member/group grants, configured policy, readiness projection and support-safe copy. Their
small configuration classes only wire typed settings and required dependencies. Chat, Files
and Calendar consume the shared cached-health contract; Chat and Files own their existing
release-readiness diagnostics. Boards, Meetings, Office, Decisions, Manuals, DevOps and Agent
Runtime retain existing declarations under their business namespaces until their actual
runtime modules move. This preserves existing feature behavior without declaring future
features production-ready or adding placeholder projects.

The former global Workspace settings record is replaced by shared shell/admin settings and
ten owning domain settings records, preserving existing `weave.workspace.*` keys, defaults
and configured readiness semantics. Core retains Keycloak identity/role/group authorization,
deny behavior and the accepted closed twelve-field snapshot projection. An absent domain has
no installed declaration, settings, state, release diagnostic or grants; an installed disabled
domain retains its explicit disabled state. Duplicate domain/capability ownership and grants
outside a domain's declared keys fail immediately. Device token claims cannot restore an
absent domain. Existing production compatibility constructors and the six-field snapshot
constructor are removed; explicit full-installation helpers exist only in test sources.

Public full-runtime User/Admin serialization and required schema fields remain unchanged.
Reduced-runtime publication and optional wire metadata still require accepted specification
changes and actual selected-runtime composition; this internal absence support does not
activate that pending public behavior. Platform manifest capability lists now derive from
the owning declarations rather than another App-owned feature list.

The combined checkpoint command is `./gradlew :server:core:check :server:core:model:check
:server:app:test checkOpenApiContractFresh`: 96 Core tests, 3 Core model tests, 1,389 App tests
(24 skipped), and 14 actual OpenAPI export tests, with no failures/errors; five OpenAPI
normalization tests also pass. The App skips are 23 PostgreSQL-lane checks and one unavailable
MinIO fixture. Real PostgreSQL qualification remains a separate required lane. New ownership
checks cover absent versus disabled installation, role denial despite provider readiness,
duplicate ownership, undeclared grants, absent-domain device claims, actual Boot discovery/
non-web exclusion, and independent binding of existing domain settings keys/defaults.

Combined checkpoint validation after capability/profile/device/Space/HTTP extraction:

```sh
./gradlew :server:core:check :server:core:model:check :server:app:test checkOpenApiContractFresh
DOCKER_HOST=<disposable-local-engine> ./gradlew :server:app:postgresJpaTest
```

Both passed. Core: 96 tests, model: 3, App: 1,389 (24 skips), actual full User/Admin export:
14, API normalization: 5; zero failures/errors. The ordinary lane runs without Docker access;
its skips are 23 PostgreSQL-gated cases plus the unavailable MinIO fixture. The separate
PostgreSQL lane passed all 200 tests with zero skips. MinIO is not qualified by PostgreSQL.
Existing architecture/provider/Java/Gradle/docs checks pass. These are whole-App/persistence
checks of the current extraction, not proof of selected runtime packaging or release delivery.

`coreArchitectureCi canonicalDataCi specCorpusConformance acceptanceContract` also passed
against the exact accepted corpus checkout. The acceptance mapping guard first identified
four stale retired-JPA source references and an obsolete App factory-test marker. Mapping
now points at the actual owning entities and real Core imports-discovery singleton assertion,
while retaining the Boards no-fallback assertion and unchanged behavior tests. This repairs
existing evidence references rather than substituting planned evidence. The legacy E2E
structure helper's optional local manifest-path cross-check is unavailable in this isolated
worktree layout; the separate pinned corpus conformance gate ran and passed.

### Device authentication filter prerequisite

The actual `security.device.DeviceCredentialAuthenticationFilter` now belongs to Core and
is registered by its servlet imports. It requires the real credential authority and error
writer instead of a nullable fallback. Files and Calendar own their `DeviceCredentialHttpScope`
path declarations; the shared filter contains no feature IDs or DAV path list. Existing
Basic decoding, minted JWT claims, expiry/revocation enforcement and denial envelope stay
unchanged. The private decoded credential value also redacts string output.

Core's empty-domain discovery constructs the filter with no permitted path scope. Tests
prove omitted/sibling paths cannot invoke credential authentication and duplicate path
ownership fails. Existing full Files/Calendar credential lifecycle/HTTP and workload-security
regressions, architecture checks and fresh full User/Admin API export passed with Core checks:
`:server:core:check :server:app:test --tests '*FilesCalendarFacadeControllerTest'
--tests '*ServerArchitectureBoundaryTest' --tests '*FilesWebDavSecurityConfigurationTest'
--tests '*CalendarMcpSecurityConfigurationTest' checkOpenApiContractFresh`. No persistence
mapping or transaction changes are included in this follow-up.

### Files runtime extraction checkpoint

The actual `:server:domains:files` project now compiles its complete serving implementation:
Files controllers/wire values, authenticated use cases, native/Nextcloud/S3 provider adapters,
typed settings, provider/bootstrap/health/capability/Space contributions and actual Spring
integration. Fifty-six former App production files moved to Files business packages; there
are no legacy source-set aliases or forwarding copies. Model and persistence remain useful
separate projects. App composes the runtime dependency and excludes its business namespace
from transitional scanning while retaining Boot's test/auto-configuration filters.

Files runtime imports register actual servlet beans/controllers; persistence imports register
both actual transactional JPA adapters beside scoped mappings/repositories. Controller and
Repository annotations retain their handler-discovery and exception-translation meanings;
component scanning is not their registration mechanism. Discovery without App proves bean
uniqueness, real Files request mappings, absent Calendar/Chat implementation classes and no
serving providers/controllers/native filesystem construction in non-web contexts. Required
local Files storage and SecretRef-root configuration remain explicit; no live provider or
mounted credential is needed by the isolated discovery fixture.

Nine independent unit classes moved with the runtime. Existing full-App HTTP, PostgreSQL,
locking, native composition and backup/restore harnesses continue using the extracted code.
The App scaffold no longer registers Files lock/mutation/bootstrap beans or owns their
settings. Files owns organization-binding status probes; shared binding-status values/port
belong to Core, and App projects the unchanged accepted Admin wire shape. The future
FilesDocs canonical interoperability facade remains outside release Files.

The existing resolver/catalog now accepts an explicit owning-task closure instead of an App
class inventory. Files no longer applies the verification bridge; an empty legacy inventory
never creates an unfiltered App test task. Selection precedence/pinning is unchanged. The
same existing partial-App guard also protects actual API and schema exports: until selected
App composition is implemented, reduced exports reject rather than rewrite full contracts
under a reduced identity. Negative executions preserved existing export hashes and mtimes.
The Files library is independently buildable; a complete Files-only App executable is not
yet claimed.

OpenDAL 0.50.0 and the unchanged platform-classifier resolution now belong to Files. The
shared domain-library build preserves Java parameter metadata needed by Spring HTTP/AOT
reflection, previously supplied implicitly by the executable Boot plugin. The real BDD
credential creation/revocation path exposed and verified this prerequisite. Actual full App
JAR inspection finds the Files runtime/model/persistence nested JARs, the actual controller,
use case and imports resource in the runtime JAR, and no Files implementation classes in
App's own classes. This Mac build uses `osx-aarch_64`; Linux ARM64/native-image qualification
is separate. Existing finite native/S3 startup-selector debt remains tracked under #1736.

Final source validation: `:server:domains:files:check`, whole `:server:app:test`,
`checkOpenApiContractFresh`, `coreArchitectureCi` and `:server:app:bootJar -Pdomains=all`
passed together in 1m48s. Results: Core 98, Core model 3, Files runtime 94 (including two
actual discovery contexts), Files model 21, Files persistence 11 and App 1,307 tests, with
zero failures/errors. App retains 24 explicitly skipped PostgreSQL/MinIO cases in that lane;
actual OpenAPI export runs 14 tests and normalization runs five. The separate full
`:server:app:postgresJpaTest -Pdomains=all` with scoped disposable OrbStack PostgreSQL passed
200 tests with no skips/failures/errors in 2m59s. Selected Files aggregate checks also pass.
No persistent operator database, owner data or migration history was reset.

### Protected HTTP client integration checkpoint

The existing HTTP-client implementation from PR #1732 is incorporated by normal branch merge,
with relocation conflicts resolved at actual owners. Core Identity now owns the human Keycloak
administration client, named HTTP interface, exact operation admission/status policy and the
original invitation settings binder. Existing Core Security token and SecretRef implementations
remain unchanged; no duplicate OAuth2 stack or Core-to-Agent Runtime dependency is introduced.
The redundant incoming settings snapshot and production test-only constructor are removed.
Independent client, policy and managed-fixture tests moved physically to Core.

Actual servlet auto-configuration discovers the protected beans once without App or optional
domains, with no credential read or remote call during construction; non-web discovery omits
them and the operator composes its explicit transport. Authentication, timeouts, URL context
paths, request-local fixture admission, exact successful statuses and 401 invalidation remain
covered. Files owns its specialized Nextcloud transport factory. Calendar/Chat/Boards factories
have their business packages; their remaining App runtime location stays transitional. DAV and
Matrix retain their specialized transport semantics. The existing HTTP implementation record
links to the canonical Java standard instead of defining a second rule set.

`./gradlew :server:core:check :server:core:model:check :server:domains:files:check
:server:app:test checkOpenApiContractFresh coreArchitectureCi -Pdomains=all --console=plain`
passed in 1m57s: Core 157/model 3, Files 94/model 21/persistence 11, App 1,262 with 24 explicit
PostgreSQL/MinIO lane skips, OpenAPI export 14 plus normalization five; zero failures/errors.
`WEAVE_SPEC_CORPUS_ROOT=<exact-accepted-checkout> ./gradlew canonicalDataCi
specCorpusConformance acceptanceContract --console=plain` passed in five seconds. No persistence
mapping changed; the prior Files checkpoint's 200 real PostgreSQL cases are historical evidence,
not a new database run for this HTTP-only source. Production inventory: 909 file FQNs, no duplicates.

The proposed corpus PR #172 now also reconciles the old AMD64 component partition with the
owner's automatic Linux ARM64 target and explicitly describes digest-bound private GHCR
transport from related PR #1733. Proposal revision `e38e9ba` passed manifest generation, pinned
corpus lint/contract examples and links; implementation still pins accepted `f049c0b18794`.
No delivery contract was activated. Read-only registry inspection verified the pinned Keycloak
index's ARM64 manifest `sha256:89d56312ace1d9b0bf8168f1a92ea135ba185d2844285f7651cb50774e47c1d8`;
a disposable ARM64 container produced the existing required services-JAR SHA-256
`b295c806047aea4b3ca31352c1664bff698106013902cb2b66f0cd1a61c2ad83`. This is dependency validation,
not a Weave ARM64 image, native executable or release qualification.


### Calendar runtime and shared Identity checkpoint

Calendar now owns its actual serving runtime under `:server:domains:calendar`: authenticated
use cases, User/DAV/setup controllers and wire values, native JPA provider, CalDAV transport,
recurrence/parsing/rendering, settings, binding bootstrap and health/capability/Space contributions.
Sixty App production files moved physically. Normalized mappings/store and native JPA adapter
share the owning `calendar.persistence` namespace; pure model remains separate. Existing
package-private mappings stay private rather than becoming a cross-domain API. Calendar's
`ical4j` dependency moved with its runtime consumers. Bootstrap business validation lives in
an application class; configuration only constructs its runner.

Module-local runtime imports register the actual servlet graph after scoped persistence.
Three standalone contexts prove native and CalDAV construction without live providers/secrets,
bean uniqueness, actual handler registration, no serving graph in non-web tooling and absence
of Files OpenDAL, Matrix JNI and Chat persistence implementations. App excludes the owning
namespace from transitional scanning, preserving Boot filters. There are no legacy source-set
aliases, forwarding controllers or duplicate implementations. Actual JAR inspection finds the
Calendar runtime/model/persistence libraries and their imports/handlers, with zero copies of
the sixty moved implementations in App classes.

Independent units move with Calendar. Two suites using the full installation fixture remain
App integration tests in Calendar business packages. The resolver schedules real owning tests;
only Chat retains the transitional App bridge. Its inventory test now distinguishes actual
runtime projects by their task closure rather than a Files-only name exception. Rendered
mobile-profile results belong to application instead of provider implementation. Source guards
continue inspecting actual API/persistence/provider paths without loosening the boundary rule.

Core Identity now owns current-member/display-name queries, invitation use cases and HTTP
adapters, profile/identity HTTP adapters, invitation JPA adaptation and pure invitation
values/ports. The shared in-memory invitation test adapter is a test fixture. App/operator
repository duplicates and the invitation service's default-identity test convenience constructor
are removed. Existing User/Admin URLs, authorization, provider sequencing, idempotency and
wire shapes remain unchanged. Organization administration/Weaver entitlement separation and
remaining foundational Space controllers still require their later ownership cleanup.

HMAC reference construction no longer reads a mounted secret during AOT/context construction.
The ordered serving readiness runner validates the exact SecretRef before normal startup
workflows/traffic; first use also fails closed. Same key-length/symlink/readability checks and
one-time key caching remain. Tests cover unavailable/short/symlinked secrets, a mount arriving
after construction, cached-key behavior and the actual imports-discovered readiness hook.
Standalone discovery verifies actual shared controllers and no non-web serving graph. MVC
slices explicitly import relocated handlers while full runtime uses module-local discovery.
Calendar and Files one-time credential DTOs now redact diagnostics without altering JSON or
credential accessors; owning regressions preserve the wire values and exclude sensitive output.

Final main-source combined command:
`./gradlew :server:domains:calendar:check :server:core:model:check :server:app:test
checkOpenApiContractFresh coreArchitectureCi :server:app:bootJar -Pdomains=all --console=plain`
passed in 3m3s. Core 186/model 3,
Calendar 71/model 4/persistence 10, Files 95/model 21/persistence 11, App 1,169 with 24 explicit
PostgreSQL/MinIO lane skips, OpenAPI export 14 and normalization five; zero failures/errors.
The strengthened Calendar discovery assertions also passed selected Calendar checks afterwards.
`:server:domains:check -Pdomains=calendar` passed; resolver 32 tests. `:server:app:entitySchemaCheck`
passed against the selected compiled graph. The separate scoped disposable PostgreSQL command
`:server:app:postgresJpaTest -Pdomains=all --console=plain` passed 200 tests with zero skips,
failures or errors in three minutes. No owner data, database or migration history was reset.
Production inventory: 912 file FQNs, no duplicates. Distinct context-lane scheduling is a
remaining build cleanup, not a completed qualification claim.

### Flutter stable navigation checkpoint

The existing Flutter application now shares `ShellDestination` identities between route
composition, navigation semantics and member-Space refresh. Indices only adapt that list to
GoRouter/Material widgets. Production still uses the accepted full shell and bootstrap rules;
there is no reduced-manifest activation, provider inference or separate per-selection client.
New widget fixtures prove omitted/reordered destinations retain correct routes and semantics at
ordinary and doubled text scale. Existing English/German large-text targets, keyboard access,
bootstrap/sign-in redirects and navigation continue passing.

Pinned local Flutter 3.41.6 / Dart 3.11.4 / Rust 1.95.0: `flutter pub get`, `flutter gen-l10n`
and the repository build_runner command passed without dependency upgrades; only the router's
owned generated hash changed. `flutter test --no-pub test/core/router test/features/shell`
passed 80 tests. Final `flutter analyze --no-pub --fatal-infos` passed and
`dart format --output=none --set-exit-if-changed .` checked 482 files with zero changes.
The first widget run built the pinned local Matrix bridge; this is not server Linux ARM64,
GraalVM native or two-member product acceptance. Full application/native/HIL lanes remain separate.
