# Files domain

Files owns authenticated file use cases, generated User endpoints, optional DAV/setup
endpoints, metadata, locks, visibility and storage/provider behavior. App is the only
executable; Files is a reusable runtime library. Build inclusion and persisted runtime
provider binding are separate decisions.

## Project and package ownership

| Project | Responsibilities | Dependency direction |
| --- | --- | --- |
| `:server:domains:files` | Actual controllers, DTOs, use cases, provider implementations, settings and serving integration | Core contracts/runtime, Files model and persistence; never App |
| `:server:domains:files:model` | Framework-free values, canonical use cases and persistence/blob ports | Java only; no Spring, JPA, App or SDKs |
| `:server:domains:files:persistence` | JPA entities, IDs, repositories and transactional adapters | Files model and JPA; no App, HTTP controllers or providers |

Runtime packages under `com.massimotter.weave.backend.files` identify their owner:

- `api` owns thin HTTP controllers and wire values; `api.dav` owns DAV transport values.
- `application` owns authenticated use cases, captured binding resolution, path handling,
  fenced mutations, lock coordination and explicit Files bootstrap.
- `domain` and `port` hold business rules and the small provider/storage boundaries.
- `provider` implements native OpenDAL filesystem, Nextcloud DAV and existing S3 behavior.
- `config`, `capability` and `spaces` own typed wiring, Files readiness/policy declarations
  and Files contributions to shared Space workflows.
- `autoconfigure` publishes actual serving composition.

Core supplies organization identity, Keycloak authorization, shared credential/security
services, capability policy, audit, operation intents, bindings and Space contracts.
Files calls its own ports and captures the current organization binding for an operation;
it does not call App services or another domain's persistence implementation.

## Runtime discovery and provider lifecycle

`FilesRuntimeAutoConfiguration` is registered in this module's
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
Its explicit imports install actual services, controllers, providers and owned wiring.
Controller stereotypes remain HTTP handler metadata. App scans only its own composition/entry-point
packages, preserving Boot's test/auto-configuration filters; it does not maintain a second list of
Files configurations.

Files persistence imports register both actual JPA adapters alongside their scoped entities
and Spring Data repositories. `@Repository` remains exception-translation metadata; scoped
App scanning prevents duplicate registration. The adapters do not depend on App component scanning. Serving
configuration is servlet-only: schema and operator discovery constructs no Files providers,
controllers or filesystem/native operators. The historical migration catalog remains in
App's schema tooling until the separately qualified Entity-first transition; this extraction
does not remove migrations, rewrite stored state or authorize resetting owner data.

All existing configuration keys, provider account restrictions, credential resolution,
DAV semantics, captured binding revisions and write/audit fences remain in force. The finite
legacy native/S3 startup selectors remain tracked debt under #1736; moving them is not a
claim of completing provider migration/switching. Native storage still validates its local
root and initializes the pinned OpenDAL implementation at serving startup. Nextcloud mounted
credentials remain resolved for the authorized configured account at request time. Construction
must not probe a live provider. AOT/native/platform qualification is separate from JVM discovery.

The OpenDAL SDK and existing platform-classified native dependency belong to Files. AWS SDK
usage belongs to the actual future Agent Runtime consumer, not this Files implementation.
The future `FilesDocsDomainFacadeService` remains with the canonical interoperability spine;
it is not a release Files use case or a dependency of this runtime.

## Checks and remaining composition work

From repository root:

```sh
./gradlew :server:domains:files:check
./gradlew :server:domains:check -Pdomains=files
./gradlew :server:app:test
./gradlew :server:app:postgresJpaTest
./gradlew checkOpenApiContractFresh coreArchitectureCi
```

`test` runs direct unit cases; `contextTest` runs the tagged application-context cases
with separate reports. Owning `check` requires both through the shared library mechanics.

Files checks execute real owning unit tests plus Core/model/persistence prerequisites.
The Files verification bridge and its hand-maintained App unit inventory are retired.
Independent provider, resolver, facade/User use-case and security tests belong to this runtime.
`FilesFacadeServiceTest` uses the actual Files capability contribution and a focused Files-only
lock context. Existing application-level HTTP/security composition, PostgreSQL, native integration,
locking and backup/restore harnesses remain in App. No test source or dependency points into App.

Discovery uses Boot imports, asserts singleton services/request mappings and omitted Calendar/Chat.
Selected App composition now builds real reduced executables and exports contracts to separate
selection directories. The packaged PostgreSQL lane also checks signed member admission and actual
generated Dart bootstrap/navigation. Formal #172 acceptance and live/provider/platform qualification
remain distinct; owning-library checks alone cannot establish full product acceptance.

## Cleanup record

| Removed location/registration | Replacement and reason |
| --- | --- |
| `weave-files-core/` | `model/`; one physical framework-free model owner |
| App `service/FilesFacadeService`, `service/files`, Files controllers and `model/files` | Runtime `application`, `provider`, `api`; complete vertical business ownership |
| App global Files settings and Files bootstrap types | Runtime `config`/`application`; settings and lifecycle follow their consumer |
| App `WeavePersistenceConfiguration` Files beans | `FilesRuntimeAutoConfiguration`; actual owned lock/mutation/bootstrap composition |
| App component registration of Files JPA adapters | Persistence imports; reusable scoped registration without App scanning |
| App/OpenDAL dependency and classifier resolution | Files build; SDK/native inputs follow provider implementation |
| Files verification-only Gradle bridge | Actual runtime Java project/tests; selected checks execute owning production code |
| Old root model Gradle script copies | Shared mechanics in `server/gradle/scripts/domain-library.gradle`, dependencies in owning builds |

These changes retire obsolete locations and registration mechanisms, not product features,
database migrations or stored state. Keep the model/persistence boundaries and runtime imports
when adding a Files provider or use case.
