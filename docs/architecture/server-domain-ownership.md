# Server ownership and domain consolidation

Implementation: #1745; integration: #1741; PR: #1746. This document is the single living
six-phase execution plan and implementation checkpoint. It records source and checks, not
specification authority or release qualification. Earlier checkpoint detail remains in Git history.

## Authority and current outcome

Primary classification: `cross-repo-contract`; implementation owner: `weave`; normative owner:
`weave-specs`. Continue `refactor/1745-domain-ownership`, inspect/fetch before edits, preserve
unrelated work and incorporate upstream normally. Never reset to a historical checkpoint.

The accepted corpus remains `f049c0b1879414eb529a3df83a18224407f1a436`. Entity-first ownership
was approved through weave-specs#170. Explicit persistence/composition/no-Chat and Linux
ARM64/GHCR reconciliation is proposed in [weave-specs#172](https://github.com/masssi164/weave-specs/pull/172),
proposal `e38e9ba`; its hosted conformance passed, acceptance remains pending. A proposal does not
silently replace the accepted pin. No placeholder Matrix URL, owner-data reset, applied-history
rewrite, protected merge, deployment or release publication is authorized by this refactor.

**Implemented:** Files, Calendar and Chat own actual services, HTTP adapters, wire values,
provider integration, configuration, runtime resources, persistence and owning tests. Existing
Boards, Office, DevOps, Agent Runtime, Integrations, Guest Access, Portability, Decisions and
Meetings implementations have real owning runtime projects. Their incomplete implementations
remain explicit future scope. Core owns independently useful platform capabilities.

**Still open:** remaining shared App/Core administration and future App declarations; actual selected App/schema/API/artifact composition; future-runtime release
exclusion; accepted reduced/no-Chat discovery and coordinated client activation; final variants
and platform/integration qualification. Partial App packaging/API/schema export guards remain.
A green full experimental `all` build is not a qualified three-domain release candidate.

## Living six-phase execution plan

| Phase | Concrete milestone / decision | Required checks | State and remaining blockers |
| --- | --- | --- | --- |
| 1. Ownership and contracts | Inventory production classes, resources, SDK/native inputs and tests; assign actual owners before moves; reconcile corpus through normal review | Accepted corpus, call sites, bean/resource/reflection/generated consumers, ownership/source inspection | Inventory and explicit proposal implemented; acceptance of #172 pending |
| 2. Runtime architecture | Sole executable App; justified Core responsibilities; real domain Gradle runtimes with module-local imports and separate non-web persistence | Compiled graph, real Boot imports discovery, singleton beans, no App dependency | Runtime owners extracted; generic catalogs now compose owner declarations; remaining Admin ownership open |
| 3. Vertical extraction and ORM | Move actual controllers/use cases/providers/settings/resources/JPA; preserve future functionality; remove ordinary Calendar/Matrix JDBC CRUD | Owning units; PostgreSQL reads/writes/isolation/constraints/rollback/locking/concurrency; no legacy copies | Actual release and existing future runtimes extracted; provider-replacement planning now owned by Portability; shared Admin/catalog projection still transitional |
| 4. Core and build finalization | Remove optional implementation dependencies from shared workflows; one resolver drives checks, runtime, schema/API and identity | Architecture, selected artifact/SDK inspection, deterministic schema, real Gradle wiring | Shared session/Identity/Spaces/HTTP/policy/health/readiness contracts implemented; selected App composition unfinished |
| 5. Runtime and clients | Actual release plus Files-only/Calendar-only/Chat-only startup, honest endpoints/capabilities, generated User/Admin consumers and stable Flutter navigation | API/auth/freshness, reduced variants, actual absence of omitted SDK/JNI, Compose/two members, ARM64/AOT where available | Full experimental runtime checks only; reduced discovery acceptance and live/platform qualification pending |
| 6. Review and delivery | Consolidate canonical docs/scoped instructions/Skills; review combined source; coherent commits/push and PR evidence | Applicable combined gates, diff/source review, exact revision, hosted results | Checkpoints pushed; latest future-runtime combined verification passed; final selected runtime work remains; PR stays draft |

## Ownership map and dependency direction

| Previous location / responsibility | Actual owner and action | Rationale and dependencies |
| --- | --- | --- |
| Root Server executable, nested wrapper/settings and task paths | `server/app`; move executable, retire nested build | App is composition/application entry points; repository root wrapper is authoritative |
| Files global service/controller/model/provider and root Files libraries | `server/domains/files` runtime + model/persistence; move/delete old copies | Storage authority, User/DAV APIs, mutation/ETag semantics, OpenDAL/native/Nextcloud/S3 settings and integration belong to Files |
| Calendar global service/controller/model/config and root libraries | `server/domains/calendar` runtime + model/persistence | Time/recurrence, native JPA, CalDAV/iCal4j, setup/DAV/User transport and provider credentials belong to Calendar |
| Chat/Matrix global services, handlers, settings, canonical JPA and JNI build | `server/domains/chat` runtime + persistence | Canonical conversation/sync/device/crypto and Synapse/Ruma integration belong to Chat; retain only the historical JNI ABI package inside its owning project |
| Combined Chat facade's Decision Ledger and Meeting Capsule | `decisions` and `meetings` runtimes; split | Existing future capabilities use narrow Chat admission; explicit required Chat dependency, never hidden inclusion |
| Boards/Office/DevOps global services, controllers, wire models, providers and resources | Separate owning future runtimes | Preserve actual local/gated/disabled behavior without production-readiness or release-inclusion claims |
| Root application-core Agent Runtime and root provider/security/JPA; remaining App runtime | `agent-runtime` runtime + model/persistence/provider/security | Execution, lifecycle/admin/profile transport, workload registration, encrypted state, signing/wrapping and their SDKs are optional future capability |
| Connector/Interop handlers, sandbox/signature/consent logic and schemas | `integrations` runtime | Own actual future interoperability; shared membership/security stay Core; no production delivery invented |
| Guest-specific handlers, policy service and wire values | `guest` runtime | Explicit future policy/pending invitation; shared organization membership and Identity invitations remain Core |
| Migration planning/evidence + canonical resumable transfer | `portability` runtime + model/persistence; consolidate | One portability/transfer owner; pure values/ports, existing mappings and transactional adapters remain distinct boundaries |
| Organization bootstrap App persistence/use case/HTTP/wire | Core `identity.organization` + framework-free values/port; move | Shared organization authority works without optional domains; preserve recovery keys, versioned JPA updates, audit and exact HTTP contract |
| App Admin provider-replacement planning + migration evidence dependency | Portability application/API; split | Future planning and evidence are excluded together; generic Admin no longer requires the optional evidence repository |
| Shared member/session revocation formerly Matrix-named | Core Security | Required without Chat; preserve table/hash semantics, tenant authorization and fail-closed behavior |
| Global provider/category and canonical metadata registries | Generic policy/HTTP/DTOs in Core Discovery; actual module metadata in each owner | Instance catalogs reject duplicate/conflicting ownership and compose installed declarations; Office/Integrations contribute separate document-category fragments without a new domain dependency |
| Home HTTP/use case and Files recent activity | Core Home composition + Files activity source | Generic shell consumes installed owner contributions; Files owns its audit projection/privacy policy |
| Platform HTTP/manifest/status assembly | Core Discovery + Chat Matrix and Files/Calendar diagnostics | Shared assembly has no optional implementation import; provider-specific checks remain with consumers |
| App workload decoder/public-route/operator branches | Files/Calendar security, Chat/Agent route declarations, Agent ServiceLoader commands | Exact security semantics and offline command behavior preserved; omitted implementations can leave the artifact |
| Identity/member/invitation/profile, org admission, Spaces, audit, operation intents, binding policy, health | Core responsibility packages | Independently useful platform capabilities; no optional-domain implementation dependency |
| Ordinary global metadata facades with empty item lists | Retire unused service/guard graph; retain consumed Admin metadata as `discovery.admin.SuiteDomainDefinition` | No production handler/consumer used the facade graph; Admin response values remain unchanged |
| Standalone generated clients, MCP and product-test projects | Retain outside Server | Separate consumer/process/test ownership; sharing Java does not make them Server internals |

App composes Core and runtime libraries. Domains use Core contracts and explicitly required narrow
business APIs; no domain depends on App or another owner's entities/repositories. Stable IDs cross
persistence boundaries. Core consumes small domain-owned capability, Space, binding or health/readiness
contributions; it does not discover optional implementations through a service locator. No circular
Gradle/Spring dependency, cross-domain ORM relationship, source-set alias or forwarding controller is
an extraction mechanism. Public DTOs are separate from managed JPA entities.

Core's justified responsibilities are Identity/organization admission and member lifecycle; shared
Keycloak authorization, sessions, workload admission and credentials; foundational Spaces/membership;
cross-domain audit; durable operation/idempotency/binding contracts; discovery, health, schema and
shared HTTP/error infrastructure. Each owns its persistence/adapters/settings beneath its responsibility.
There is no generic Core common/util/service/controller/model/persistence/adapters dumping ground.
The framework-free `core/model` project is a deliberate contract boundary, not a home for features.

App's built-in Manuals/help capability declaration currently describes an application-wide UI entry,
with no provider, persistence or separate implemented backend feature. Do not invent a placeholder
Manuals/notifications Gradle project merely to relocate declaration metadata. Remaining global feature
declarations must be reconciled with accepted composition before claiming release exclusion complete.
Generic provider/category/canonical catalogs no longer embed optional-domain declarations. The full
experimental installation preserves twelve ordered status categories, eight selectable provider
categories and twelve canonical entries; these are distinct from the twelve selectable Gradle runtimes.
Unknown catalog policy and duplicate owner/module/alias claims fail explicitly. Office and Integrations
share a category through independently owned fragments; neither acquires a new required dependency.

## Domain integration and provider lifecycle

Every actual runtime and reusable persistence/Core integration registers fully qualified
`@AutoConfiguration` classes through its own
`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
App maintains no second manual domain configuration list. Explicit imports/beans and scoped entity/repository
registration replace broad domain scans. Pure model libraries have no fake auto-configuration.

Servlet graphs are separate from schema/operator persistence. Owning discovery tests use the published
imports resources, actual handlers and shared prerequisites, and verify uniqueness, omitted sibling
implementations and non-web exclusion. Manual slice imports alone are not discovery proof.

Providers are runtime state, not build selections. Preserve existing adapter aliases, authentication,
allowlists, timeouts, exact statuses and provider gates; the finite legacy-selector ratchet is not enlarged.
Synapse secrets, Identity HMAC, signing/wrapping/credential mounts and S3 credentials are deferred for
construction/AOT; ordered actual serving readiness and first use retain fail-closed validation. Operator
READ_WRITE initialization remains explicit; READ_ONLY readiness never creates keys or changes permissions.
Issuer-only JWT discovery uses the pinned cached supplier and retains validators/timeouts; a signed
loopback regression proves zero construction calls and real first-use verification/cache behavior.

Chat owns typed operations on the consumed Matrix protocol port. DAV, Matrix and JNI retain appropriate
transport boundaries. Shared session revocation works without Chat. Decision/Meeting stores key organization,
Space and conversation; their in-memory implementation remains incomplete future scope. Disabled Office,
DevOps and Guest behavior stays honest. No test double becomes a production fallback or readiness signal.

## Persistence and significant removals

JPA entities lead ORM-expressible persistence. Calendar's `NativeCalendarRelationalStore` now uses owning
JPA mappings and bounded candidate queries; normal Matrix E2EE CRUD uses typed normalized entities.
Preserve tenant/null/time/recurrence, uniqueness, locking/version, idempotency and transaction semantics.
The narrowly tested PostgreSQL absent-row advisory fence remains for a concrete serialization reason;
it is not a parallel SQL CRUD layer. Remote provider writes are not undone by local rollback.

Core member administration fixes an actual concurrent operation-claim defect: uniqueness recovery occurs
after the failed independent PostgreSQL transaction ends, followed by exact organization/key/request lookup.
Real tests cover concurrent claims, replay/isolation and independence from outer rollback. Portability's
transfer mappings consolidate under its persistence owner without changing table identity or locking.

Generated schema remains a deterministic derived artifact of compiled mappings and an exporter outside
the serving executable. The historical complete Flyway catalog remains App-owned while the qualified
Entity-first initializer/baseline transition is unfinished. No silent removal of unmapped integrity rules,
Flyway clean/repair, non-empty database adoption or persistent-data reset is allowed.

Significant retirements:

- Root application-core/generic JPA/provider/security projects and five shadowed adapters: owning libraries
  replace them, preserving the active stricter token, SecretRef and exact-audience behavior.
- Nested Server wrapper/settings and obsolete source/task/Docker paths: root build plus sole App executable.
- Verification-only App domain bridge and `domain-project.gradle`: genuine selected owning unit/context tasks.
- Combined `ChatFacadeService` and seeded-message scaffold: actual Chat admission/provider replacement plus
  separate future Decision/Meeting use cases; preserve real tests and tenant boundaries.
- Unused generic `domainfacade` graph and nine scaffold-only tests: actual Files/Calendar/Boards policy,
  audit and provider tests replace historical acceptance references. Its definition enum had one Admin
  metadata consumer missed by the initial service-name search; the consumed metadata is preserved in a
  narrow value, with unchanged response values. No public endpoint or real guard is removed.
- Unused package-private migration approval-copy helper: no callers; actual audited approval use case remains.

Historical closure reports retain provenance; they do not qualify changed source. No trivial getter needs
an independent migration entry. Owning READMEs record actual replacements, limitations and commands.

## Selection, generated APIs and artifacts

Use PR #1744's existing resolver exactly once: explicit valid input, then an exactly matching source branch
pin, otherwise `all`. Explicit `all` overrides a pin. PR target `dev` does not make the source pinned `dev`.
The catalog includes actual extracted runtime projects, including future experiments; `all` never means
only release domains. The automatic release pin stays `files,chat,calendar`. Invalid required-domain
selections and cycles fail clearly rather than silently including excluded modules.

Owning checks aggregate only selected domain checks and genuine shared prerequisites. Unit, real-context,
PostgreSQL and expensive integration lanes remain separate. Selected or per-domain CI rows reuse the resolver,
include explicit dependency closures and distinguish source/domain reports. Forward shell arguments with
`"$@"`, never `eval`; workflow input has no literal `all` default that suppresses pins.

Actual selected App runtime dependencies, schema/API export and artifact identity remain unfinished and
guarded. The required final chain is selection → compiled selected sources/resources → real registered
handlers → separate User/Admin exports → generated consumers/freshness. Use task prerequisites, not ordering
hints. Never reuse a stale full-domain contract or treat hidden beans/skipped tasks as artifact exclusion.
Cache/signing inputs include actual source/resources/build/native pins, source/spec identity and platform/flavor.

Flutter uses stable shell destination identities for routing/navigation/semantics/member refresh. Its accepted
full bootstrap remains active. Omitted-domain navigation and no-Chat Matrix discovery require accepted contract,
server/generated consumer and client validation changes together. No placeholder protocol URL or extra client
build is an alternative. Existing GHCR work must reconcile immutable consumers with automatic Linux ARM64;
JVM ARM64 is not GraalVM Native Image. No runner rebuild or unrelated delivery rewrite belongs here.

The unused `agent-runtime/provider/.../KeycloakRuntimeEntitlementAuthority` was retired after
consumer, registration, resource and reflection inspection. Serving shared entitlements retain their
Core Keycloak authority; active runtime identity integration is unchanged. The unused test-only
organization bootstrap file adapter is also retired; the actual versioned JPA adapter remains.

## Validation and next execution checkpoint

The Home/Platform/security follow-up passed the combined command below in 5m29s
(`/tmp/weave-home-platform-security-candidate.log`); source was frozen throughout:

```sh
DOCKER_HOST=<observed-disposable-engine> WEAVE_SPEC_CORPUS_ROOT=<accepted-pinned-checkout> ./gradlew :server:domains:check :server:app:test :server:app:postgresJpaTest :server:app:entitySchemaCheck coreArchitectureCi canonicalDataCi specCorpusConformance acceptanceContract checkOpenApiContractFresh infraStatic :server:app:bootJar -Pdomains=all --console=plain
```

Fresh XML: Core 310 unit cases; App 841 with 23 PostgreSQL-only skips; separate PostgreSQL
204 with no skips/failures/errors. Actual domain/Core context lanes, schema/API freshness,
architecture, corpus, static acceptance and infrastructure gates pass. Static acceptance mapping
is not live integrated product evidence. Initial checks exposed a missing Core-owned Platform
properties registration; production auto-configuration now registers it independently of App.

Home aggregation/HTTP now belongs to Core Home, consuming actual owner section/activity contributions.
Files owns recent activity privacy and projections. Audit readback uses an organization-scoped,
filtered, ordered and paginated JPA query; no unscoped production read remains. PostgreSQL proves
cross-organization isolation and bounded entity loading; Home tests prove privacy across pages.
Platform HTTP/assembly/wire models now belong to Core Discovery. Matrix validation/projection belongs
to Chat, and Files/Calendar own their status diagnostics. Shared assembly tolerates absent domains;
the accepted required Matrix manifest still fails support-safely when absent pending #172.

Files/Calendar own their signed workload decoders; actual signed-token tests preserve issuer,
audience, type, expiry and exact-scope enforcement. Chat/Agent own their existing public-route
contributions; protected route tests remain. Offline Agent key commands use module-owned Java
ServiceLoader registrations. Three actual packaged-JAR probes (both commands with invalid arguments
and an unknown command) returned status 2 with support-safe diagnostics and no Spring startup.
App production source now imports no optional-domain implementation types.

Previous checkpoint `fd0937f73b` passed organization bootstrap/catalog/replacement extraction and
full checks. Independent resolver/workflow/inventory tests passed 54 cases, actual Gradle wiring
six cases, and default/Files+Chat owning checks. Hosted earlier API/protocol/Admin/architecture/docs/
corpus/infra jobs passed; selected owning checks passed on Linux ARM64. The separate hosted reduced
export wiring probe repeatedly failed reading an ephemeral Gradle binary store and remains open.

Actual App `bootJar -Pdomains=files,chat,calendar` and `bootJar -Pdomains=files` still reject at the
partial-packaging guard. Selected production dependencies, isolated API export, artifact contents
and startup must pass before retiring it. All-domain integration test dependencies must never
pollute selected export/runtime classpaths. Remaining shared Admin logic and unconditional future
metadata still need actual ownership review. No Compose lifecycle/provider URL/persistent data changed.

Remaining external qualification: accepted #172 where affected; Linux ARM64 JVM/AOT/native/JNI;
exact-candidate Compose and two ordinary members/two real Flutter iOS apps; physical preparation and
explicit HIL. Missing, skipped, failed and unavailable checks stay distinct. Do not present this
checkpoint as completion of #1745.
