# Java and Spring implementation standard

This is Weave's single coding standard for Java/Spring implementation and review. The pinned corpus in `specs/weave-specs.lock.json` still owns product meaning. Existing code and historical architecture documents are not permission to repeat a known violation. The [agent skills](agent-skills.md) apply this standard through task-specific workflows.

## Small, declarative, readable code

- Prefer framework-supported declarations over handwritten plumbing: HTTP interfaces, Spring Data repositories, Bean Validation, typed configuration and existing mapping/generation tools.
- Prefer Java Stream API for clear collection transformations (`map`, `filter`, `flatMap`, grouping and reduction), immutable results, method references and small pure functions. Prefer an ordinary loop when it makes state, early exits, error handling or resource lifetime clearer.
- Do not hide writes, authorization, retries or remote effects in `peek` or dense stream chains. Do not introduce `parallelStream` around JPA, request/security context or provider calls; concurrency needs explicit bounds, context propagation and tests.
- Apply Clean Code: one responsibility, intention-revealing names, small cohesive operations, early validation, no swallowed errors, and comments about invariants rather than restating syntax. Avoid speculative abstractions and an interface/implementation pair for every class.
- Use records for immutable values and transport DTOs where appropriate. Records are not JPA entities. Prefer targeted Lombok such as `@RequiredArgsConstructor` or selected accessors when supported by the module; keep explicit constructors when they express validation or qualifiers more clearly. Do not add a global Lombok dependency merely to shorten one constructor. Any adoption needs centrally pinned compatible dependencies and annotation-processor wiring.
- Do not use blanket `@Data`, generated mutable-key equality, or relationship-traversing `toString` on entities. Do not let record/Lombok-generated methods expose secrets. Keep entities behind persistence adapters and preserve the accepted schema lifecycle.

## Ownership and package placement

- `weave-application-core` and `weave-files-core` stay framework-free: canonical values, use cases and ports, not Spring/JPA/Jackson/HTTP types.
- Provider/security/persistence adapters implement those ports. Preserve the module-specific architecture tests; a generic hexagonal tutorial allowing Spring in application core does not override them.
- `server` composes adapters and delivers authorized facades. Group feature logic by feature; group integration wiring under `config/<integration>/`. Do not move business logic into a global `config` or `util` package.
- For a Keycloak-style integration, the intended composition layout is `config/keycloak/KeycloakAdminClient.java` (HTTP contract), `KeycloakAdminProperties.java` (typed non-secret deployment settings where applicable), and `KeycloakAuthConfig.java` (registration/authentication wiring). Provider behavior and domain mapping remain in the adapter. This is a layout rule, not a claim that the current production refactor has landed.
- Keep generated Weave User/Admin clients and DTOs generated. Specialized southbound DAV, Matrix/Ruma/JNI and security protocols may retain a justified dedicated adapter; do not force every protocol through a generic REST abstraction.

## Which bean, and when?

| Responsibility | Preferred construction | Important boundary |
| --- | --- | --- |
| Canonical value, pure function or framework-free use case | Plain Java; compose externally if needed | No Spring annotations in core modules |
| Spring-owned feature service or delivery adapter | Appropriate stereotype and constructor injection | Respect modules that deliberately forbid component scanning |
| Third-party object, port adapter or integration wiring | `@Bean` in `@Configuration(proxyBeanMethods = false)` | Pass dependencies as method arguments; do not call another bean method directly |
| Ordinary HTTP service proxy | Interface plus `@ImportHttpServices` | No `@Component` on the interface; no redundant manual proxy factory |
| Deployment settings | Validated `@ConfigurationProperties` record/class | Register once using `@EnableConfigurationProperties` or deliberate scanning |
| Reusable library default | Boot auto-configuration and justified back-off conditions | `@ConditionalOnMissingBean` is not a way to hide broken application wiring |
| Several implementations of a port | Explicit adapter catalog and runtime resolver | `@Qualifier` disambiguates injection; `@Primary` does not choose an organization's active provider |
| Request-bound credential/context | Existing request scope or explicit per-call context | Never put one member's mutable credentials in a singleton |

Prefer stateless/thread-safe singletons for ordinary services. Use prototype/request scope or `ObjectProvider` only for a demonstrated lifetime/optional dependency requirement, not to mask circular dependencies. `@Lazy` is not a substitute for safe readiness. Provider adapter/client construction must not perform provider writes, migration, provider-credential acquisition or network readiness probes; mandatory process infrastructure such as database/TLS initialization retains its fail-fast startup contract.

Use `@Validated`, appropriate Jakarta constraints, nested `@Valid` and typed `Duration`/URI values. Required infrastructure fails fast; safe defaults must be explicit. Avoid scattered `@Value` expressions and duplicate custom settings for values already owned by Boot. Tests must show successful binding, invalid/missing mandatory input, and exactly one intended registration.

## Configuration ownership

| Kind of value | Owner and delivery |
| --- | --- |
| Process infrastructure: database, listener, deployment trust, storage mount | Typed deployment configuration; environment overrides or mounted configuration when appropriate |
| Secret material | Approved secret store/mount and scoped reference; resolve at the boundary that needs it, never in source, image layers, logs or generated `toString` |
| Active provider, organization/module binding, revisions and migration state | Persisted server application state, changed through authorized runtime operations |
| Provider-specific endpoint/settings for a runtime binding | Versioned protected configuration referenced by that binding; not an environment-selected global singleton |

No external `WEAVE_*_PROVIDER` selection is required in the target architecture. This does **not** mean deleting database/TLS/secret delivery configuration or hardcoding credentials. Existing operator-provisioned catalogs are implementation constraints to migrate deliberately, not a license to invent a new settings authority.

## Runtime provider replacement and AOT

The server stays running during provider replacement. The set of supported adapters is available in a stable Spring bean graph; the active persisted binding is runtime data. Do not choose it with an environment variable, `@Profile`, `@ConditionalOnProperty`, `@ConditionalOnExpression`, `@Primary`, bean replacement or application-context restart. AOT freezes bean definitions, not the state and routing decisions of existing beans.

Required invariants for implementation and review:

1. Exactly one active binding per organization/module; a staged target receives no member traffic. Do not merge providers into one member request.
2. Resolve and authorize a binding at operation start and retain its revision for in-flight work; validate revision/fencing at mutation/cutover boundaries.
3. An inactive or unconfigured provider cannot bring down the server. Credential/configuration readiness is checked before activation and use, with support-safe failure; mandatory process infrastructure remains fail-fast.
4. Secure and retain old-provider state/access for reconciliation and coherent rollback. Copy/delta-sync and verification precede fenced cutover; never stop the server first, delete source data, reset volumes or weaken permissions to make switching work.
5. Preserve persisted checkpoints, idempotency, stable IDs, references and effective permissions. A local `@Transactional` boundary cannot roll back remote provider side effects.
6. Preserve existing `JpaProviderBindingRepository` and `MigrationApplyGateService` blockers until real inventory, target readback, authorization parity and recovery evidence satisfy the accepted contract. Admin selection metadata is not active routing authority.

The accepted release contract already makes canonical Chat state Weave-owned; the Chat provider is a replaceable projection/synchronization target. Other modules retain their specified ownership. The broader question of copying payloads to a new provider versus retaining them entirely in Weave remains a **per-domain product decision**, not an agent implementation shortcut. Files adoption/migration belongs to [#1498](https://github.com/masssi164/weave/issues/1498); Calendar provider-pair migration remains deferred from the current release.

### Explicit existing debt

At the introduction of this standard, Chat, Calendar and Boards still contain startup-selected composition. Files already has durable per-operation routing, but some adapter registration and readiness remain deployment-selected. Legacy environment selectors exist in base and production application configuration. The finite inventory in `tools/fixtures/java_agent_legacy_selectors.json` is a no-growth ratchet, not accepted target architecture or proof of live migration. [#1736](https://github.com/masssi164/weave/issues/1736) owns its removal: replace the affected routing safely, then remove the selectors and their environment wiring. Do not merely delete configuration and claim the feature works.

## Declarative HTTP boundaries

Use explicit `@GetExchange`, `@PostExchange`, etc., under an optional shared `@HttpExchange`. A Java method called `getUser` does not infer GET. Prefer `@ImportHttpServices` with a named group and Boot's `spring.http.serviceclient.<group>.*` settings for fixed deployment integrations. Use `RestClientHttpServiceGroupConfigurer` for group-scoped transport/authentication. A static base URL must not replace organization-scoped runtime binding resolution.

Keep authorization and credentials scoped to the correct client and operation. Preserve token ownership, expiration/invalidation, `private_key_jwt`, exact SecretRef access, allowlists, timeouts, status mapping and redaction. Do not relay member tokens to provider administration, retry writes implicitly, or add a global bearer interceptor. Manual factories or imperative calls require a concrete API/protocol limitation and tests, not habit.

The test-only examples under `server/src/test/java/com/massimotter/weave/backend/config/reference/` prove Boot registration, typed properties, JSON mapping, group isolation, request-time credentials and no implicit 401 retry. They are **not** production Keycloak protocol/security acceptance; that refactor remains [#1730](https://github.com/masssi164/weave/issues/1730).

## Native/AOT build and acceptance

Read versions from `gradle/libs.versions.toml` and the affected module, not an upstream sample. At this standard's introduction the server uses Boot 4.1.0 and Java 21 source/toolchain, and its existing `native-runtime.gradle` builds Rust JNI, not a GraalVM executable. No native readiness is implied by this document.

- Separate Java source compatibility from the native builder: Boot 4's supported native path requires a compatible GraalVM 25+ toolchain. Pin a compatible native-build-tools version centrally; verify the exact Boot patch and Gradle combination before adding it.
- Boot's `org.graalvm.buildtools.native` integration supplies AOT task wiring; JVM-AOT can use `org.springframework.boot.aot` explicitly. Confirm tasks exist before invoking module-qualified `:server:processAot`, `:server:nativeCompile` or `:server:nativeTest`. Do not present those commands as available before plugin integration.
- AOT must see supported adapter definitions without real provider availability or secret access. Use Spring-supported registrations and narrow reviewed runtime hints for reflection/resources/serialization/proxies/JNI. Do not indiscriminately register entire packages or commit sensitive tracing-agent output.
- Validate the actual native artifact's auth and declarative clients, JSON, PostgreSQL/schema lifecycle, startup/shutdown and required OpenDAL/Rust JNI paths. Native tests need compatible fakes or real isolated dependencies; Mockito is not made generally native-safe by changing its mock maker.
- Qualify each target OS/architecture/libc and native library classifier. A macOS ARM binary is not a Linux ARM container binary; do not assume cross-compilation or the Mac mini's container architecture.
- Distinguish JVM tests, AOT processing, native compilation, native execution and exact-image integration evidence. A successful build is not native readiness. Record RSS, startup latency, throughput, workload, image digest and platform before claiming resource improvements.
- Build changed components on the appropriate hosted builder; reuse only verified artifacts with complete input hashes. Publish/consume the exact qualified immutable images without rebuilding on Dogfood. [#1731](https://github.com/masssi164/weave/issues/1731) owns the GHCR transport change; it does not itself establish GraalVM qualification.

## Verification and reference sources

Run from the repository root:

```sh
./gradlew javaAgentContractCheck :server:javaAgentReferenceTest
```

These are bounded contract/reference checks. The reference task uses only Jupiter and does not require Rust/JNI, Docker or provider credentials. It does not replace affected module ArchUnit tests, production security/HTTP tests, authoritative PostgreSQL tests, full `serverCi`, native execution or same-process migration integration. CI also runs the static contract guard through `docsStructureCheck`; no extra resource-heavy lane is added for skill prose alone.

Primary framework references: [HTTP services](https://docs.spring.io/spring-boot/reference/io/rest-client.html), [external configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html), [Gradle AOT](https://docs.spring.io/spring-boot/gradle-plugin/aot.html), [native-image constraints](https://docs.spring.io/spring-boot/reference/packaging/native-image/introducing-graalvm-native-images.html), [system requirements](https://docs.spring.io/spring-boot/system-requirements.html), and [Spring native known limitations](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-with-GraalVM). Versionless pages may describe a newer patch; they never authorize an unrequested upgrade. Use the corpus pin for product ownership and migration acceptance.
