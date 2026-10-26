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
proposal `caf3d00` (including typed installed workspace statuses and consumer behavior); local corpus lint passes, acceptance remains pending. A proposal does not
silently replace the accepted pin. No placeholder Matrix URL, owner-data reset, applied-history
rewrite, protected merge, deployment or release publication is authorized by this refactor.

**Implemented:** Files, Calendar and Chat own actual services, HTTP adapters, wire values,
provider integration, configuration, runtime resources, persistence and owning tests. Existing
Boards, Office, DevOps, Agent Runtime, Integrations, Guest Access, Portability, Decisions and
Meetings implementations have real owning runtime projects. Their incomplete implementations
remain explicit future scope. Core owns independently useful platform capabilities.

**Review fixes implemented:** the workspace wire uses required Core statuses and a typed
installed-domain map. No-Chat manifest/status omit conditional Matrix metadata; Chat validates it.
Generated Dart and the single Flutter shell consume stable installed identities independently of
readiness or member permission. App's fabricated Manuals backend declarations are retired; Help
remains a client shell entry. Calendar candidate bounds, batching and cohesive service ownership
have owning PostgreSQL/unit coverage. These changes are prepared against pending #172; acceptance
and live release qualification remain distinct. The initializer/schema cutover, Linux ARM64/GHCR
and full live/native qualification remain separate work. A green experimental `all` build is not a
qualified three-domain release candidate.

## Current review-fix execution

Continue from the fetched branch; review reference `f2aabb1c5f` is not a reset target.
Scope is the four source findings in review 5647885198 and follow-ups 6423999001/6424005994.
Prepare the coordinated pending #172 contract and executable implementation/tests without repinning
or claiming formal acceptance. Preserve the existing extraction and selection authority.

| Phase | Concrete milestone and decision | Checks | Current state / remaining blocker |
| --- | --- | --- | --- |
| 1. Reproduce and coordinate | Verify the release decoding/no-Chat failures; propose required Core statuses plus typed installed-domain map and conditional Matrix semantics | Red Core serialization/config tests; accepted corpus inspection; #172 lint/contract vectors | Reproduced and corrected; proposal `caf3d00` awaits formal acceptance; accepted pin unchanged |
| 2. Calendar interval correctness | Correct DATE upper-day ceiling and bounded legal-offset candidates; exact recurrence filtering stays authoritative; batch scoped payloads | Owning PostgreSQL candidate/query/free-busy cases for exact January windows, touching edges, DST/offsets, overrides, FLOATING/UTC, tenant/scope/deletion, 205 events and PostgreSQL tie ordering | Implemented in `3a553dbeb5`; 13 PostgreSQL cases pass, no skips; no SQL CRUD, whole-calendar scan or result truncation |
| 3. Test ownership and cohesive services | Move direct Calendar/Files/Chat tests; Core owns only the shared selection fixture; split Calendar event, authorization, DAV sync and client setup responsibilities | Actual owner unit/context reports; imports discovery; App security slices; DAV serialization/token-order regressions | Implemented in `a285644ac8`; owning checks exclude App/unrelated runtimes; composition/database tests remain App-owned |
| 4. Usable selected product | Typed workspace transport, manifest/status, generated consumers, stable single-client routes, strict installed Chat errors; retire fake App Manuals metadata | Real selected JAR/PostgreSQL/JWKS ordinary-member HTTP; generated Dart bootstrap, stored manifest and production router/shell; anonymous/wrong-org/Admin-client negatives | Implemented in `a285644ac8`; release and three individual selections pass the same packaged lane; formal contract activation awaits #172 |
| 5. Combined verification | Freeze runtime source and check release, Files, Calendar, Chat plus full experimental composition | API/client freshness, owning/App/PostgreSQL, architecture, corpus, static acceptance, docs and infrastructure gates below; Flutter analysis/full suite | Exact commands/results belong to the current PR checkpoint; static and local JVM proof do not establish live/native acceptance |
| 6. Review and delivery | Review source/security/transactions/resources, consolidate canonical guidance, preserve coherent commits and draft PR | Diff/source review; normal push; finding-to-commit/test report; proposal approval separated from implementation readiness | No merge/deploy/data reset/publication; #172 acceptance and separate schema/platform/live qualification remain external |

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
| Global Admin service/controller/17 wire types and suite feature constants | Core `admin.application/api` + actual domain declarations; split | Shared admission/audit/selection policy remains Core; Files owns required unverified-activation policy; optional suites, API links and disabled MCP preview disappear with owners |
| App feature defaults/environment aliases | Nine owning domain resources; move | Preserve consumed scalar defaults/aliases and deployment override precedence; reusable capability configuration loads its owner defaults; retire one unused Files selector |
| Unsupported Notifications metadata only | Retire production declaration and matching conformance fixtures | No implemented backend, handler or accepted bounded context; future product scope remains, without a fabricated project/readiness claim |
| Shared member/session revocation formerly Matrix-named | Core Security | Required without Chat; preserve table/hash semantics, tenant authorization and fail-closed behavior |
| Global provider/category and canonical metadata registries | Generic policy/HTTP/DTOs in Core Discovery; actual module metadata in each owner | Instance catalogs reject duplicate/conflicting ownership and compose installed declarations; Office/Integrations contribute separate document-category fragments without a new domain dependency |
| Home HTTP/use case and Files recent activity | Core Home composition + Files activity source | Generic shell consumes installed owner contributions; Files owns its audit projection/privacy policy |
| Platform HTTP/manifest/status assembly | Core Discovery + Chat Matrix and Files/Calendar diagnostics | Shared assembly has no optional implementation import; provider-specific checks remain with consumers |
| App workload decoder/public-route/operator branches | Files/Calendar security, Chat/Agent route declarations, Agent ServiceLoader commands | Exact security semantics and offline command behavior preserved; omitted implementations can leave the artifact |
| Identity/member/invitation/profile, org admission, Spaces, audit, operation intents, binding policy, health | Core responsibility packages | Independently useful platform capabilities; no optional-domain implementation dependency |
| Ordinary global metadata facades with empty item lists | Retire unused service/guard graph; retain consumed Admin metadata as owner-declared suite fragments | No production handler/consumer used the facade graph; Admin response values remain unchanged |
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

App no longer declares Manuals as an installed backend capability. The removed six metadata/
configuration classes had no backend implementation, provider, persistence or endpoint; advertising
ready functionality from App conflated shell Help with installation. Flutter's Help route remains.
No placeholder Manuals project replaces them. Generic catalogs compose actual owner declarations;
the selectable Gradle catalog remains independent of public status/category counts.
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

Selected App production dependencies and isolated schema/API export now use the existing resolver.
All-domain integration libraries/fixtures are test-only. The implemented chain is selection → compiled
selected sources/resources → real registered handlers → separate User/Admin exports → consumer checks. Use task prerequisites, not ordering
hints. Exports have selection-specific output/report directories; full tracked freshness explicitly requires
`all`, while reduced normalization stays in build output. The executable manifest records normalized
`Weave-Domains`. `verifySelectedRuntime` rejects excluded transitive projects; `verifySelectedArtifact`
checks actual JAR libraries, manifest and duplicate Weave classes. BootRun builds JNI only with Chat.
Never reuse a stale full-domain contract or treat hidden beans/skipped tasks as artifact exclusion.
Cache/signing inputs include actual source/resources/build/native pins, source/spec identity and platform/flavor.

Flutter uses stable shell destination identities for routing/navigation/semantics/member refresh. The
coordinated Server/generated-consumer/client candidate supports omitted domains and no-Chat Matrix
discovery against proposed #172; accepted-contract completion still requires its normal approval. No placeholder protocol URL or extra client
build is an alternative. Existing GHCR work must reconcile immutable consumers with automatic Linux ARM64;
JVM ARM64 is not GraalVM Native Image. No runner rebuild or unrelated delivery rewrite belongs here.

The unused `agent-runtime/provider/.../KeycloakRuntimeEntitlementAuthority` was retired after
consumer, registration, resource and reflection inspection. Serving shared entitlements retain their
Core Keycloak authority; active runtime identity integration is unchanged. The unused test-only
organization bootstrap file adapter is also retired; the actual versioned JPA adapter remains.

## Verification and qualification boundary

Run repository-pinned Java/Gradle and Flutter 3.41.6, initialize client dependencies with
`flutter pub get --enforce-lockfile` from `client/`, and use an observed disposable Docker engine.
The accepted corpus checkout must match `specs/weave-specs.lock.json`. The implementation commits
are `3a553dbeb5` (Calendar) and `a285644ac8` (workspace/clients/test ownership/service cohesion).
Test follow-up `5ceb23944f` preserves experimental selections without inventing workspace
contributions for modules that do not declare them. The PR records the exact final command outcomes and per-selection reports; earlier checkpoint
logs and counts remain in Git history rather than competing current instructions.

```sh
for selection in files,chat,calendar files calendar chat; do
  ./gradlew :server:domains:check :server:app:verifySelectedArtifact \
    :server:app:selectedApiContractCheck :server:app:selectedRuntimePostgresTest \
    -Pdomains="$selection" --console=plain
done
./gradlew :server:domains:check :server:app:test :server:app:postgresJpaTest \
  :server:app:entitySchemaCheck coreArchitectureCi canonicalDataCi \
  specCorpusConformance acceptanceContract checkOpenApiContractFresh checkClientUserApiFresh \
  :weave-user-api-client:check :weave-admin-api-client:check infraStatic docsCheck \
  javaAgentContractCheck :server:app:javaAgentReferenceTest coreDocsCheck -Pdomains=all --console=plain
# From client/:
flutter analyze --no-pub
flutter test --no-pub
flutter test --no-pub --dart-define=WEAVE_OFFLINE_CONTRACT_ONLY=true test/live_stack_contract_test.dart
```

Reports under `server/app/build/test-results/selectedRuntimePostgresTest/<selection>` remain
separate. Their Flutter subprocess executes the normally gated packaged-consumer widget case;
ordinary Flutter runs skip that case until the real JAR/identity/database lane supplies its inputs.
App unit/context checks explicitly skip PostgreSQL-only cases; `postgresJpaTest` executes those
contracts and includes selected Calendar's owning `postgresTest`. Omitted domains must leave both
serving classes/libraries and exported handler paths; no-Chat artifacts exclude Chat-only JNI.

Passing corpus conformance confirms consumption of the unchanged accepted pin; it does not approve
proposed public behavior. #172 must be reviewed/accepted, followed by an explicitly reviewed repin
and conformance rerun. Schema initializer integrity/recovery cutover, Linux ARM64 image/native
qualification and exact-candidate Compose/two ordinary members/two real Flutter iOS applications,
physical preparation and HIL remain separate. This review fix authorizes none of deployment,
publication, protected merge, persistent-data reset or rewriting applied history.

## Review-fix behavior and test ownership

`WorkspaceCapabilitiesResponse` requires `shellAccess`, `adminControlPlane` and a typed `domains`
map. A domain key means its runtime is compiled into the installation. Enabled/readiness values
represent configuration and operation health; policy/grants represent the current member. An
installed but unavailable domain keeps its navigation entry. An omitted domain has no entry,
endpoint or shell destination. Unknown stable IDs do not become positional tabs. Required
`protocols` remains an object; Matrix URL/status are absent without Chat and validated with Chat.
The generated Dart decoder rejects a missing/non-map installation container and malformed entries,
including in release mode, instead of silently defaulting the installation to empty.

The selected PostgreSQL lane starts the actual selected JAR, an isolated PostgreSQL and the existing
loopback JWKS identity fixture. A signed ordinary member goes through the serving JWT/security
chain; anonymous and wrong-organization requests are denied. A Flutter subprocess retrieves actual
manifest/status/capabilities through generated operations, validates bootstrap and uses the decoded
snapshot with the production router/shell. Its UI repositories are offline doubles: this proves
bootstrap/navigation, not provider writes, browser OIDC, two-device collaboration or HIL.

Calendar facade/user service tests, Files facade tests and Chat facade/binding/quarantine/Space
access/native-provider/Matrix-error-writer tests now live in their owning runtime projects. Core owns the tiny in-memory provider
selection test fixture. App retains actual composition/security slices and PostgreSQL integration
fixtures. Calendar's separate `postgresTest` is a selected prerequisite of App `postgresJpaTest`;
ordinary domain checks keep fast units/context lanes separate from infrastructure tests.

Calendar temporal bounds and the retired facade responsibilities are documented in the Calendar
README. DATE and instant intervals remain half-open; conservative candidate bounds cannot discard
an occurrence that exact expansion would accept. No timezone default, fixed offset conversion,
handwritten CRUD, schema authority or persistent-data reset was introduced.
