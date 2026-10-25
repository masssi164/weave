# Files domain

Files owns its file metadata, locks, user-resource visibility and storage behavior. Build-time domain inclusion and runtime provider selection are separate decisions.

## Current ownership

| Project | Owns | Must not depend on |
| --- | --- | --- |
| `:server:domains:files:model` | Framework-free values, application contracts and persistence ports | Spring, JPA, Server, provider SDKs |
| `:server:domains:files:persistence` | Files JPA entities, IDs, Spring Data repositories and both transactional JPA adapters | Server, controllers, provider implementations or other domains |

`JpaFilesAuthorityRepository` and `JpaFilesUserResourceRepository` are now compiled beside their package-private JPA types. Their existing transactions, lock fences, conflict translation and SQL mappings are unchanged. There is no second copy in the executable Server.

Shared Java/toolchain/test mechanics live in `server/gradle/scripts/domain-library.gradle`. Each library keeps its own dependencies. Domain-qualified Gradle groups and archive names avoid collisions when other domains also have `model` and `persistence` children.

## Still transitional

The Files HTTP controllers, authenticated use cases, storage/provider adapters and runtime Spring composition still live in the legacy executable `server`. Their extraction, module-local `AutoConfiguration.imports`, selected `server/app` packaging and reduced-domain discovery remain open in #1745. Do not advertise a complete Files-only runtime from these library checks.

Existing application-level PostgreSQL tests continue exercising the moved implementations through the unchanged Java package and type names. Historical Flyway migrations and retained data are not removed or rewritten.

## Checks

Run from the repository root:

```sh
./gradlew :server:domains:files:check
./gradlew coreArchitectureCi
```

The domain checks cover model boundaries, entity mappings, adapter ownership and forbidden cross-domain dependencies. Full Server/PostgreSQL and API contract gates remain required for integration.

## Cleanup record

| Removed location | Replacement and reason |
| --- | --- |
| `weave-files-core/` | `server/domains/files/model/`; one physical Files-model owner |
| `server/app/src/main/java/.../files/adapter/JpaFilesAuthorityRepository.java` | Files persistence library; eliminate the split package between Server and JPA library |
| `server/app/src/main/java/.../files/adapter/JpaFilesUserResourceRepository.java` | Files persistence library; keep transactions with the entities they operate on |
| `model/gradle/scripts/java-and-dependencies.gradle` | Model dependencies stay in its small build file; common mechanics use the shared domain-library script |
| `model/gradle/tasks/verification.gradle` | JUnit Platform setup is shared, not copied for every domain library |

These are source/build cleanups, not deletion of planned product features, database migrations or stored state. Keep removal rationale here and in commits; do not retain commented-out implementations.
