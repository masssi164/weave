# Files domain agent rules

Files is a feature module. Keep its Java model Spring-free, keep its JPA entities and repositories in its `persistence` child Gradle project, and keep provider-specific network or filesystem SDKs in this domain alone.

- Never move Files entities to `server/core`, `weave-persistence-jpa`, or the executable App.
- JPA entity and repository changes require domain-owned tests; the complete Flyway history stays in the Server App's one authoritative schema catalog.
- A Files controller must route through a Files use case/port, not call Nextcloud, OpenDAL, or Spring Data directly.
- Domain wiring uses a module-local `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` when its runtime autoconfiguration is extracted.
- Resolve `-Pdomains` from shared selection rules; never silently ship Files when excluded.
- Keep APIs and records free of provider secrets, raw URLs containing credentials, or member token forwarding.
- Favor immutable records and declarative Spring interfaces; no blanket Lombok `@Data` on JPA entities.

Current extraction is partial: pure Files model and its JPA mappings moved first; controllers and provider adapters are still in the transitional Server and should be migrated with their dependencies next.

From repository root:
```sh
./gradlew :server:domains:files:check
./gradlew coreArchitectureCi
```
