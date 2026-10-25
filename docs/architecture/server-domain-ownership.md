# Server ownership and domain consolidation

Implementation owner: #1745; integration parent: #1741; PR: #1746. This is the current implementation guide, not specification-corpus authority or release qualification.

## Current state versus target

**Implemented:** Files owns its pure model and transactional JPA adapters. Calendar owns its
pure model, normalized mappings and production JPA payload store. Core owns shared persistence
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

**Still transitional:** The remaining services/controllers, provider runtime wiring,
real selected-runtime packaging, future-domain
release exclusion and reduced discovery/client integration are not complete. The verification
bridge and partial-packaging guard remain until those behaviors are implemented. No ARM64,
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

- `:server:domains:files:model` and `:server:domains:files:persistence`: see `server/domains/files/README.md` for ownership and removal rationale.
- `:server:domains:calendar:model` and `:server:domains:calendar:persistence`: see `server/domains/calendar/README.md`.
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
| 2. Domain composition | App is the sole executable; Core owns justified platform responsibilities; runtime modules own imports registration and scoped persistence; separate non-web tooling | Compile, dependency graph, real imports discovery and bean uniqueness | App executable split and scoped persistence imports/discovery implemented; domain runtime extraction still pending |
| 3. Vertical extraction and ORM | Files/Calendar/Chat own services/controllers/providers/persistence; future domains remain buildable outside release; convert ordinary Calendar/Matrix JDBC CRUD | Domain units, PostgreSQL writes/isolation/constraints/transactions, no legacy copies | Calendar/Matrix ORM conversion and persistence-library extraction implemented; runtime/API still open |
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
