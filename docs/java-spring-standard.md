# Java and Spring implementation standard

This is Weave's single coding standard for Java/Spring implementation and review. The pinned corpus in `specs/weave-specs.lock.json` owns product meaning. Existing code and historical architecture documents are not permission to repeat a known violation. The [agent skills](agent-skills.md) apply this standard through task-specific workflows.

## Small, declarative, readable code

- Prefer framework-supported declarations: HTTP interfaces, Spring Data repositories, Bean Validation, typed configuration and existing mapping/generation tools over handwritten plumbing.
- Prefer Java Stream API for clear collection transformations, immutable results, method references and small pure functions. Use an ordinary loop when state, early exits, error handling or resource lifetime are clearer.
- Do not hide writes, authorization, retries or remote effects in `peek` or dense stream chains. No `parallelStream` around JPA, request/security context or provider calls; concurrency needs explicit bounds, propagation and tests.
- Apply Clean Code: cohesive responsibilities, intention-revealing names, early validation, no swallowed errors, comments explaining invariants. Avoid speculative abstractions and an interface/implementation pair for every class.
- Use records for immutable values and transport DTOs. Records are not JPA entities. Targeted Lombok such as `@RequiredArgsConstructor` or selected accessors is appropriate where configured; use explicit constructors for validation or qualifiers. Centrally pin compatible Lombok and annotation processors; do not add a global dependency to shorten one constructor.
- No blanket `@Data`, mutable-key equality or relationship-traversing `toString` on entities. Do not let record/Lombok-generated methods expose secrets. Keep managed entities behind persistence boundaries.

## Ownership and package placement

- `server/core/model` and the owning Files, Calendar, Agent Runtime and Portability model libraries stay framework-free: canonical values, use cases and ports, not Spring/JPA/Jackson/HTTP types. The old `weave-files-core` root is retired, not another source authority.
- Domains own their entities, repositories, transactional adapters, application services, API controllers and provider dependencies. Current and target ownership are distinguished in [Server domain ownership](architecture/server-domain-ownership.md). Do not claim the App/Core/runtime extraction is finished from a model-library move.
- Core contains genuinely shared identity, authorization, Spaces, audit and narrow contracts, never optional-domain implementation. Adapters implement domain ports. Preserve architecture tests and forbid domain-to-executable or cross-domain repository dependencies.
- The executable application composes selected domains. Group integration wiring under its actual owner, for example `config/keycloak/KeycloakAdminClient.java`, `KeycloakAdminProperties.java`, and `KeycloakAuthConfig.java` for shared identity. Domain-specific provider clients belong to that domain, not global Core/config.
- Keep generated Weave User/Admin clients and DTOs generated. Specialized DAV, Matrix/Ruma/JNI and security protocols may retain justified dedicated adapters; do not force every protocol through generic REST.

## Entity-first persistence

Follow [Entity-first persistence](architecture/entity-first-persistence.md) and the explicit reconciliation in weave-specs#170. ORM-expressible structure and integrity rules belong to domain-owned entities. The baseline is generated from compiled mappings, not maintained as parallel handwritten SQL. Ordinary reads/writes use Spring Data, JPQL/Criteria, typed projections, entity graphs and declarative transactions. Database-specific operational exceptions must be narrow and justified.

The active initializer remains unchanged until generated-baseline integrity/recovery and specification cutover qualify. Do not introduce competing Hibernate/Flyway mutation, automatic update/create in serving profiles, foreign-schema adoption or an owner-data reset. The pre-release direction does not require hypothetical backwards compatibility with every development migration; it does require preserving actual integrity rules. SQL CHECK null semantics, uniqueness, indexes, locks and tenant boundaries require database tests, not merely Hibernate validation.

## Which bean, and when?

| Responsibility | Preferred construction | Important boundary |
| --- | --- | --- |
| Canonical value or framework-free use case | Plain Java | No Spring in pure model libraries |
| Spring-owned feature service | Appropriate stereotype and constructor injection | Respect explicit library composition |
| Third-party object or integration wiring | `@Bean` in `@Configuration(proxyBeanMethods = false)` | Pass dependencies as arguments; do not call another bean method |
| Ordinary HTTP proxy | Interface plus `@ImportHttpServices` | No `@Component` on the interface or redundant manual proxy factory |
| Deployment settings | Validated `@ConfigurationProperties` | Register once via explicit enabling or deliberate scanning |
| Reusable library default | Boot auto-configuration | Own `AutoConfiguration.imports`; back-off must not hide broken wiring |
| Several provider implementations | Adapter catalog and runtime resolver | `@Qualifier` disambiguates injection; `@Primary` is not organization routing |
| Request-bound credentials | Existing request scope or explicit per-call context | No mutable member credentials in a singleton |

Runtime libraries register actual integration through their module-local Boot imports, independently
of App scanning. Keep controller stereotypes required for handler discovery and meaningful
`@Repository` exception-translation metadata; prevent duplicate scanning rather than deleting
semantic annotations. The shared library build preserves Java `-parameters` metadata for HTTP
handlers/proxies and AOT; the application plugin's compiler defaults do not configure plain libraries.

Prefer stateless/thread-safe singletons. Use prototype/request scope or `ObjectProvider` only for demonstrated lifetime/optional dependencies, not circular-dependency concealment. `@Lazy` is not safe readiness. Provider/client construction performs no provider writes, migrations, credential acquisition or network readiness probes. Mandatory database/TLS infrastructure retains fail-fast initialization.

Use `@Validated`, Jakarta constraints, nested `@Valid` and typed `Duration`/URI settings. Mandatory infrastructure fails fast; defaults are explicit. Avoid scattered `@Value` and duplicate settings already owned by Boot. Test successful binding, invalid/missing mandatory values and exactly one registration.

## Configuration ownership

| Kind | Owner |
| --- | --- |
| Database, listener, deployment trust, storage mount | Typed infrastructure configuration; environment/mount delivery where appropriate |
| Secret material | Approved secret store/mount and scoped reference; never source, image layers, logs or generated `toString` |
| Active provider, organization/domain binding, revisions and migration state | Persisted application state changed through authorized runtime operations |
| Runtime binding endpoints/settings | Versioned protected configuration referenced by the binding, not environment-selected global singleton |

No external `WEAVE_*_PROVIDER` selection is required in the target architecture. Do not delete database/TLS/secret delivery or hardcode credentials. Existing catalogs are constraints to migrate deliberately, not permission for another settings authority.

## Runtime provider replacement and AOT

The server stays running during provider replacement. Included adapters form a stable Spring bean graph; the active binding is runtime data. Do not select providers via environment variables, `@Profile`, `@ConditionalOnProperty`, `@ConditionalOnExpression`, `@Primary`, bean replacement or context restart. AOT freezes bean definitions, not routing data. Build-time domain omission is a separate concern.

1. Exactly one active binding per organization/domain; staged targets receive no member traffic. Never merge provider results into one request.
2. Resolve and authorize the binding at operation start, retain its revision for in-flight work, and validate revision/fencing at mutation and cutover.
3. Inactive/unconfigured providers cannot prevent startup. Credentials/readiness are checked before activation/use with support-safe failures; mandatory process infrastructure remains fail-fast.
4. Retain old-provider state/access for reconciliation and rollback. Copy/delta synchronization and verification precede fenced cutover. Do not stop the server first, delete source data/reset volumes or weaken permissions.
5. Preserve checkpoints, idempotency, stable IDs, references and effective permissions. Local `@Transactional` cannot roll back remote side effects.
6. Preserve `JpaProviderBindingRepository` and `MigrationApplyGateService` blockers until real inventory, target readback, authorization parity and recovery qualify. Admin selection metadata is not active routing authority.

Canonical Chat state is Weave-owned; its provider is a replaceable projection/synchronization target. Other domains retain their specified ownership. Copying payloads versus retaining them in Weave is a per-domain product decision, not an implementation shortcut. Files adoption/migration belongs to #1498; Calendar provider-pair migration remains deferred from this release.

### Explicit existing debt

Chat, Calendar and Boards still have startup-selected composition. Files has durable per-operation routing but some registration/readiness remains deployment-selected. `tools/fixtures/java_agent_legacy_selectors.json` is a finite no-growth ratchet, not accepted target behavior or live migration proof. #1736 owns removal: replace routing safely, then remove selectors and environment wiring. Do not simply delete configuration and claim success. Source checks include the extracted domain libraries and future production roots.

## Declarative HTTP boundaries

Use explicit `@GetExchange`, `@PostExchange`, etc., under optional `@HttpExchange`; Java method names do not infer HTTP verbs. Prefer `@ImportHttpServices`, named groups and Boot `spring.http.serviceclient.<group>.*` settings for fixed deployment integrations. Use `RestClientHttpServiceGroupConfigurer` for group-scoped transport/auth. Static base URLs must not replace organization binding resolution.

Preserve operation-scoped authorization, token ownership/expiry/invalidation, `private_key_jwt`, exact SecretRef access, allowlists, timeouts, status mapping and redaction. No member tokens to provider administration, implicit write retries or global bearer interceptor. Imperative clients/manual factories need an actual API limitation and tests.

Reference tests in `server/app/src/test/java/com/massimotter/weave/backend/config/reference/` prove registration, typed properties, mapping, group isolation, request-time credentials and no implicit 401 retry; they do not prove production Keycloak security. That refactor remains #1730.

## Native/AOT build and acceptance

Read versions from `gradle/libs.versions.toml` and the owning module. At this standard's introduction Boot is 4.1.0 and source/toolchain Java 21; `native-runtime.gradle` builds Rust JNI, not a GraalVM executable.

- Separate source compatibility from the native builder. Boot 4's supported path requires compatible GraalVM 25+; centrally pin native-build-tools and verify exact Boot/Gradle compatibility rather than silently upgrading.
- Boot's `org.graalvm.buildtools.native` integration supplies AOT wiring; JVM-AOT can use `org.springframework.boot.aot`. Confirm tasks before advertising `:server:app:processAot`, `:server:app:nativeCompile` or `:server:app:nativeTest`.
- AOT sees adapter definitions without live providers or secret access. Use supported registrations and narrow reflection/resource/serialization/proxy/JNI hints. Never register entire packages indiscriminately or commit sensitive agent output.
- Validate actual native auth/HTTP clients, JSON, PostgreSQL/schema lifecycle, startup/shutdown and OpenDAL/Rust JNI paths. Compatible tests/fakes are needed; changing Mockito's mock maker does not make it generally native-safe.
- Qualify OS/architecture/libc and native library classifiers. macOS ARM binaries are not Linux ARM container binaries. Automatic delivery targets Linux ARM64; other platforms remain manual. No assumed cross-compilation.
- Separate JVM, AOT-processing, native compilation, native execution and exact-image integration claims. Record RSS, startup, throughput, workload, digest and platform before claiming savings.
- Build changed components on compatible hosted builders and reuse only verified complete-input artifacts. GHCR/Dogfood consumes immutable qualified images without rebuilding. #1731 is image transport, not Native Image qualification.

## Verification and reference sources

Actual libraries with application-context suites opt into the shared
`server/gradle/scripts/domain-library.gradle` mechanics with the local
`weaveContextTests` declaration. Their ordinary `test` task excludes the JUnit `context`
tag; `contextTest` runs only that tag with separate XML/HTML reports, and `check` requires
both. Tag a whole class only when every case constructs a context; tag individual methods
in mixed classes so direct unit cases remain in the unit lane. Spring types alone do not
make a direct-object test an application-context test. Pure models remain framework-free
and have no artificial context task. Selected domain checks retain both owning lanes and
genuine shared prerequisites; PostgreSQL and expensive integrated product evidence remain
separate from these lightweight context checks.

```sh
./gradlew javaAgentContractCheck :server:app:javaAgentReferenceTest
./gradlew coreArchitectureCi
```

These bounded checks do not replace production security/HTTP, authoritative PostgreSQL, `serverCi`, native execution or same-process provider migration. `docsStructureCheck` also includes the static guard. Skill prose adds no separate resource-heavy CI lane.

Primary references: [HTTP services](https://docs.spring.io/spring-boot/reference/io/rest-client.html), [configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html), [Gradle AOT](https://docs.spring.io/spring-boot/gradle-plugin/aot.html), [native constraints](https://docs.spring.io/spring-boot/reference/packaging/native-image/introducing-graalvm-native-images.html), [system requirements](https://docs.spring.io/spring-boot/system-requirements.html), and [known native limitations](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-with-GraalVM). Versionless pages do not authorize unrequested upgrades; the corpus pin governs product ownership and acceptance.
