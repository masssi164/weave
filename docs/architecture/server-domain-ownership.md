# Server ownership and domain consolidation

Status: architecture refinement and implementation contract for [#1745](https://github.com/masssi164/weave/issues/1745), integrated under [#1741](https://github.com/masssi164/weave/issues/1741). This document is **not** evidence that the moves, build variants, or native release have already qualified.

## Product and release decision

Weave is a **modular monolith**: one Spring Boot runtime assembled from independent Gradle projects. The **initial release** includes only `files`, `chat`, and `calendar`. Boards, Office, DevOps, Agent Runtime and other planned product domains must **remain represented as future product scope**, but their feature code must not be disguised as global Core or accidentally packaged in the release artifact.

The default Gradle domain selection remains `all` (all **currently registered/implemented** optional domains), preceded by explicit `-Pdomains` or pipeline `domains` input, then a matching `pinned_branch` / `pinned_domains` selection. Branch names resolve to exact source commits before artifact creation. Build identity, test selection, OCI publication and Dogfood consumption use the same normalized selected-domain set.

Build-time domain selection is **independent** of runtime provider binding and provider migration.

## Target project ownership

| Gradle project | Responsibility | Rules |
|---|---|---|
| `:server:app` | Only executable Spring Boot entry point, authoritative *complete historical* Flyway migration catalog, OpenAPI export, deployment composition | No duplicate domain implementation; APIs generated **after** selected controllers have compiled and their runtime mappings are present |
| `:server:core` | Spring wiring for shared identity, organization, access, Spaces, cross-domain audit, public platform config, module discovery and shared runtime authorization | No optional-domain implementations, no concrete provider client, no Files/Calendar/Chat JPA entities |
| `:server:core:model` | Immutable common value types and narrowly shared ports | Java-only, no Spring, JPA, HTTP, Jackson, provider SDK or product-specific objects |
| `:server:domains:files` | Files controllers, use cases, ports, domain DTOs, adapter orchestration, auto-configuration | Own Files JPA entities/repositories and native/Nextcloud/S3 provider integration; OpenDAL only here |
| `:server:domains:chat` | Matrix-facing controllers, canonical chat use cases, persistence, adapters, native JNI bridge, auto-configuration | Own Chat-specific Matrix room/device/crypto entities and repositories; **not** shared OIDC session revocation |
| `:server:domains:calendar` | Calendar controllers, use cases, persistence and CalDAV/iCalendar provider adapters, auto-configuration | Own Calendar entities/repositories; iCal4j only here |
| `:server:domains` | Gradle aggregator and selection-aware verification | Aggregator itself must not pull every domain into `:server:app` |
| Future `server/domains/*` | Boards, Office, DevOps, Agent Runtime, interoperability, guest-facing features, portability/transfer | Introduce individually when extracted and supported; **no empty pretend modules** and no silent feature deletion |

A shared adapter or persistence **project** is not a required architectural layer. Merge implementation into the smallest actual owner. If an implementation is genuinely shared, colocate it in the appropriate Core feature package (e.g. `core/identity`, `core/security`, `core/spaces`, `core/audit`). The exceptions are narrowly reviewed, not justification for catch-all `core/persistence` or `core/adapters`.

### Concrete source ownership decisions

* `files.*`, `service.files.*`, Files-specific `persistence.jpa.*`, Files WebDAV, Nextcloud, OpenDAL and S3 Files implementations: **Files**.
* `calendar.*`, `service.calendar.*`, Calendar-specific `persistence.jpa.*`, CalDAV and iCalendar: **Calendar**.
* `chat.*`, Matrix Client-Server projection, Matrix device/room crypto persistence and native Chat adapters: **Chat**.
* `persistence.jpa.matrix.MatrixRevokedSession*` (historical storage naming): **Core Security**, with a documented backward-compatible table/key format; do not lose revocation if Chat is omitted.
* `identity.*`, tenant admission, shared session cutoff/revocation, `security.*`, `spaces.*`, `context.authz.*`: **Core**, including their narrowly owned JPA entities, ports and repositories.
* `audit.*` and audit persistence: **Core** where cross-domain; domain-specific audit policy stays inside its domain.
* `boards.*`, `office.*`, `devops.*`, `agentruntime.*`: **future domain implementation**, not generic Core. Preserve agreed functionality and data; move instead of dropping.
* `portability.*`, `transfer.*`: candidate **future Portability domain**, preserving shared infrastructure only if genuinely used without the feature.
* `service.guest.*`: identify whether functionality is pure membership/security (Core) or the Guest Access product domain. Separate before moving.
* `config`, `controller`, `model`, `service`, `domainfacade` are **not owners**. Classify each declaration by its actual behavior; migrate under the owner or delete only verified duplication.
* Generic provider-binding *primitives* may be Core. Per-domain binding and readiness, migration, adapter lifecycle and provider dependencies are domain-owned.

## Domain internal layout

Use **one Gradle project per domain initially**, adding subprojects only for actual boundary enforcement or resource savings:

```text
server/
  app/
  core/
    model/
  domains/
    build.gradle
    files/
      build.gradle
      src/main/java/.../files/
        domain/
        application/
        port/
        persistence/          # JPA entities, repositories, adapters
        provider/             # Nextcloud / native / S3 implementations
        api/                  # controllers and wire DTOs
        autoconfigure/
      src/main/resources/META-INF/spring/
        org.springframework.boot.autoconfigure.AutoConfiguration.imports
    chat/
      build.gradle
      src/main/resources/META-INF/spring/
        org.springframework.boot.autoconfigure.AutoConfiguration.imports
    calendar/
      build.gradle
      src/main/resources/META-INF/spring/
        org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

A domain exposes narrow ports and optional contributions to shared workflows: `DefaultSpaceDomainPreparation`, `SpaceRelationshipContributor`, `DomainProviderHealthContributor`, and `DomainDescriptor`. Core sees only these contracts. **No circular Gradle dependency**, duplicated bean owner, full-package component scan, reflection-based discovery, or direct cross-domain persistence access.

Each auto-configuration has `@AutoConfiguration` and its module-local `AutoConfiguration.imports` file with its **fully qualified class name on a separate line**. Scope serving Beans to servlet runtimes as appropriate; schema initialization and Dogfood operator CLIs are distinct non-web entry points. Verify Boot's real discovery, not a manual test `@Import` shortcut.

## Minimal-code / Lombok policy

Prefer Spring's declarative `@HttpExchange` proxy clients and typed configuration where HTTP semantics and security guards remain equivalent; do not abstract away WebDAV/CalDAV verbs or Keycloak operation allowlists. Use constructor injection, Spring Boot auto-configuration, immutable records, explicit `@ConfigurationProperties` and `@ConditionalOn...` where appropriate.

Use Lombok **selectively**, only where it removes real mutable-model/constructor boilerplate without obscuring invariants. Do **not** blanket-apply `@Data`, generated `toString()`, `equals()` or `hashCode()` to JPA entities or credential-bearing objects. Avoid needless Lombok on records and one-constructor Spring services. Keep annotation processors explicitly scoped to owning Gradle projects and compatible with AOT.

Prefer streams/functional style where it materially improves comprehension. Do not introduce a generic adapter framework or a new configuration authority merely to shorten code.

## Ordered six-phase delivery

1. **Architecture planning:** inventory every production declaration in App, Core, legacy root projects and Domains; record its only owner and incoming/outgoing dependencies. Mark each as move, retain (with reason), consolidate, or delete (with reproducible redundancy proof). Explicitly review Lombok and declarative Spring opportunities. No unreviewed deletions.
2. **Domain contract:** implement one real domain's configuration/ports/JPA/provider/API layout and the three extension contracts; verify non-web CLI isolation, `AutoConfiguration.imports` and negative dependency checks. Specify a reusable pattern; do not require needless per-layer Gradle projects.
3. **Extract and consolidate:** move all Files, Chat and Calendar implementations, JPA entities and provider-specific dependencies to their owners. Move future-domain classes out of Core into separate planned projects only when real. Delete previously duplicated classes only after semantic comparison; preserve stricter auth and secret-handling implementations. Every class must have one source and JAR owner.
4. **Core finalization:** reduce Core to genuine cross-domain contracts/infrastructure; remove generic catch-all adapter/persistence projects where their code has domain owners; preserve identity, Spaces, audit, schema and durable session revocation. Remove orphaned dependencies/imports.
5. **Domain finalization:** build and run tests for all and selected domain sets; check runtime/JAR dependency graph, actual Spring discovery, reduced-context startup, Data/JPA, API/handler registration, Rust JNI, security negatives, and module-specific health. Keep all historical Flyway migration *bytes* unchanged and all retained data intact.
6. **Review and presentation:** supply before/after project and package maps, deleted/merged code and dependency counts, concrete maintainability/resource wins, interesting design decisions, evidence and open release blockers. No deployment/HIL claims from unit tests.

## OpenAPI and generated clients: enforce a real build dependency

For an `:server:app` OpenAPI export, **compile and load the selected domain controllers first**, including mapping/handler registration. `:server:app:openApiContractExport` must depend on the proper compilation/resource/test runtime tasks, not rely solely on `mustRunAfter`. Generated User/Admin clients must depend on this completed export; consumer checks must fail closed on stale or omitted operation inventory. A Files-only artifact may omit Calendar/Chat operations only under a versioned, accepted User API/client bootstrap contract. Do not alter OrgManifest v2's mandatory Matrix discovery in secret: reduced client/bootstrap support requires a governed corpus change.

## CI, selected build, runtime and release gates

Examples, once implementation qualifies:

```bash
./gradlew :server:domains:check -Pdomains=files,chat,calendar
./gradlew :server:app:bootJar -Pdomains=files,chat,calendar
./gradlew :server:app:bootJar -Pdomains=files
```

All registered current release domains are the `all` default; matching branch pins apply when no explicit selection is given. Ensure normalized domain identity, source commit, spec lock, Java flavor and `linux/arm64` platform flow through builder → GHCR digest → Mac mini consumer, with no runner rebuild. Other platforms/build flavors remain manually selectable.

**Acceptance is separate:** compile, unit/integration, API contract freshness, full historical Flyway/PostgreSQL, native/JNI, two ordinary member collaboration, exact OCI digest/ARM64 Dogfood, physical iPhone HIL. A reduced JAR that compiles does **not** establish client bootstrap or release readiness. Native Image/AOT must run same contract and evidence suite before any promotion.

## Review requirements

Every phase is a small linked PR with one release-notes label, no parallel source authority, independently reviewable failure boundaries and exact-commit CI. Do not merge the still-draft #1744 verification bridge as if it were production extraction. Do not publish the previously staged incomplete source tree as an accepted consolidation; the historical root projects and Core-owned future-domain code still require removal. No live state or persistent volumes are reset by refactoring.


## Verified blockers in the previously staged, unpublished tree

The intermediate Git tree inspected before this phase is **not safe to publish as a completed module extraction**:

- `server/domains/files`, `server/domains/chat`, and `server/domains/calendar` all resolve to the **same Git tree SHA** `19176c72abbdb590e96acda7761e4542fac153b6` — they are identical placeholder project trees, not three independent production implementations.
- Legacy `weave-application-core`, `weave-files-core`, `weave-persistence-jpa`, `weave-runtime-provider-adapters`, `weave-runtime-security-adapters` and `server/src` remain in that root tree beside the new layout.
- Its root `settings.gradle` still includes the legacy root projects and a verification-only domain bridge, rather than a proven production module graph.
- Domain ownership, actual omitted-runtime classpaths, JPA entity placement, and code-first OpenAPI order therefore require a new exact-head implementation and CI proof. Previous local assertions against a different source tree cannot be attributed to this staged Git tree.

**No WIP tree promotion, destructive path removal, speculative code deletion, or Dogfood image promotion is authorized until those blockers are resolved.**


## Measured package inventory of the unpublished intermediate tree

These are **source-file counts, not Java type/class counts**, measured from the exact uncommitted Git tree `a07667168811df73201a4022604033d83d5fb140`. They are an actionable migration backlog, **not** a measurement of code currently merged into `dev`.

| Physical location / package | Java source files | Review conclusion |
| --- | ---: | --- |
| `server/core/src/main/java` total | 440 | Excessive for the intended shared foundation; split by owner |
| `core/.../boards` | 50 | Entire future Boards domain candidate |
| `core/.../config` | 53 | Classify by bean ownership: domain-specific configs move with their domain |
| `core/.../service` | 46 | Split into identity/security/Spaces vs optional-domain use cases |
| `core/.../model` | 113 | Split wire DTOs and product models from truly shared primitives |
| `core/.../controller` | 27 | Move domain controllers; keep shared identity/platform endpoints only |
| `core/.../provider` | 27 | Split generic binding primitives from domain provider runtime |
| `core/.../domainfacade` | 17 | Remove generic catch-all ownership; delegate domain implementations |
| `core/.../identity` | 25 | Shared identity candidate, subject to dependency inventory |
| `core/model` (separate Gradle project) | 93 | 76 have future-domain-like paths; investigate individual ownership |
| `core/persistence` (separate Gradle project) | 46 | At least 13 have Agent Runtime / Portability / Transfer paths |

**Priority:** remove future-product runtime dependencies from the release Core *before* investing in superficial Lombok cleanups. Each moved source must keep one owner and preserve its JPA/database/API/security contract. Some path matches can still resolve to a shared primitive after inspection; directory naming alone is not a deletion authorization.
