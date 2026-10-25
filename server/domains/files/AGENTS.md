# Files domain agent rules

Files is a feature module. Its model stays Spring-free. Its entities, Spring Data repositories and transactional JPA adapters belong together in the domain's `persistence` library. Provider SDKs belong with Files provider integration, not generic Core.

- Never move Files entities or JPA adapters back into `server/core`, `weave-persistence-jpa` or the executable App.
- JPA changes require domain-owned tests and existing PostgreSQL integration coverage. The complete historical Flyway catalog remains authoritative; source ownership does not authorize a duplicate migration catalog or data deletion.
- A Files controller routes through a Files use case/port, not directly to Nextcloud, OpenDAL or Spring Data.
- Runtime domain wiring uses its own `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` when extracted. Do not add fake Spring configuration to the pure model.
- Resolve `-Pdomains` from shared selection rules during production composition integration; never silently ship excluded implementations.
- Keep APIs and records free of provider secrets, credential-bearing URLs and forwarded member tokens.
- Favor immutable records and declarative Spring interfaces; no blanket Lombok `@Data` on JPA entities.
- Record each obsolete path, replacement and removal reason in this domain's README and the commit. No commented-out duplicate implementations.

Current extraction: the pure model, JPA mappings/repositories and both transactional JPA adapters are domain-owned. Authenticated services, controllers, provider adapters and runtime auto-configuration are still transitional; do not claim a complete Files-only executable.

From repository root:

```sh
./gradlew :server:domains:files:check
./gradlew coreArchitectureCi
```
