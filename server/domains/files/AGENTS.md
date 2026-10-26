# Files domain agent rules

Files is a feature module. Its model stays Spring-free. Its entities, Spring Data repositories and transactional JPA adapters belong together in the domain's `persistence` library. Provider SDKs belong with Files provider integration, not generic Core.

- Never move Files entities or JPA adapters back into `server/core`, `weave-persistence-jpa` or the executable App.
- ORM-expressible schema rules belong in the owning entity annotations; ordinary data access uses Spring Data/JPA, not handwritten SQL CRUD. Follow `docs/architecture/entity-first-persistence.md` and the owner-approved transition tracked by weave-specs#170.
- JPA changes require domain-owned tests and existing PostgreSQL integration coverage. The deployed migration catalog remains transitional until generated-baseline cutover is qualified; no source change authorizes deleting owner data or silently losing SQL-only integrity rules.
- A Files controller routes through a Files use case/port, not directly to Nextcloud, OpenDAL or Spring Data.
- Runtime domain wiring uses its own `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` when extracted. Do not add fake Spring configuration to the pure model.
- Resolve `-Pdomains` from shared selection rules during production composition integration; never silently ship excluded implementations.
- Keep APIs and records free of provider secrets, credential-bearing URLs and forwarded member tokens.
- Favor immutable records and declarative Spring interfaces; no blanket Lombok `@Data` on JPA entities.
- Record each obsolete path, replacement and removal reason in this domain's README and the commit. No commented-out duplicate implementations.

Current extraction: the actual runtime, pure model, JPA mappings/repositories and both transactional adapters are Files-owned. Runtime imports register actual services/providers/controllers, while App excludes the Files business namespace from its transitional scan. Selected App production/schema/API composition and actual executable inspection now exist. Reduced bootstrap/client activation and schema/platform qualification remain separate; use the living ownership plan for exact results, and do not infer complete product readiness from packaging checks.

The resource entity now declares the active-path unique constraint, lifecycle/path check and parent index previously expressed only in V9 SQL. These annotations preserve existing behavior while making generated schemas usable; they are not a new parallel migration catalog.

From repository root:

```sh
./gradlew :server:domains:files:check
./gradlew :server:app:entitySchemaCheck
./gradlew coreArchitectureCi
```

Independent Files unit tests belong to the actual runtime project. Keep existing App integration/
PostgreSQL harnesses executing until their shared fixtures are independently owned; never add an
App dependency to make a domain test compile. Preserve controller stereotypes for HTTP handler
discovery and prevent duplicate registration through explicit module-local imports.
