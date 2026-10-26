# Entity-first persistence

Owner-approved direction, 2026-10-25. Implementation: #1745 / #1746. Normative reconciliation and runtime cutover: [weave-specs#170](https://github.com/masssi164/weave-specs/issues/170). The [Java/Spring standard](../java-spring-standard.md) applies to all contributors and repository skills.

## Authoring contract

JPA entities in the owning domain lead ORM-expressible persistence. Ordinary reads and writes use Spring Data derived repositories, typed projections, JPQL/Criteria, entity graphs and declarative transactions. Do not add parallel handwritten SQL CRUD or maintain table definitions independently. Keep provider-neutral records and API DTOs separate where their actual invariants/contracts differ; never return managed entities over the API.

Declare constraints, indexes, relations and versioning in the mappings. ORM removes duplicated manual authoring, not the database or SQL execution. A database-specific operational statement is a narrow documented exception, not another application persistence layer. Prefer records and selective Lombok without generating credential-bearing toString or unsafe entity equality.

## Implemented offline schema export

```sh
./gradlew :server:app:entitySchemaCheck
./gradlew :server:app:entitySchemaExport
```

Spring managed-type discovery and Boot naming defaults supply the actual compiled production runtime model to Hibernate's standard schema-generation API. There is no second hand-maintained entity/table inventory. Managed-type order is canonicalized. The existing domain resolver selects the production dependency graph; omitted entity libraries cannot enter the exporter through all-domain test fixtures.

Output: `server/app/build/generated/entity-schema/<normalized-domain-identity>/V1__entity_baseline.sql`. Edit entities, not this derived candidate artifact. The task requires compiled classes/resources but starts no web application, provider clients, schedulers or database. Database actions and JDBC metadata discovery are disabled; a rejecting DataSource prevents unexpected connections. No application environment credentials are read. Tests execute actual Hibernate generation.

The exporter belongs to the `schemaExport` source set, not the serving bootJar or native executable. The root architecture gate includes export and isolated contracts. OpenAPI remains a distinct generation path requiring compiled controllers and registered handlers.

## PostgreSQL qualification through ORM

```sh
./gradlew :server:app:postgresJpaTest --tests '*EntityFirstBaselinePostgresTest'
```

`GeneratedEntityBaseline` is one shared test-only fixture. It requires the PostgreSQL lane, creates a new schema in the existing test-owned container, copies the actual generated baseline to its own temporary location and executes it with Flyway. No caller can supply a live datasource. It expects exactly one initial migration, validates it and validates mapped types. It does not create another handwritten schema fixture.

`EntityFirstBaselinePostgresTest` exercises actual Files repositories and transactional adapters: writes/reads, organization isolation, private Space visibility, duplicate active paths, tombstone path reuse and repeated migration without losing rows. Deliberately malformed entities test the database constraint boundary independently of Java record validation: null/mismatched active keys, zero binding revisions and tombstones retaining active keys must fail and leave no row.

`CalendarEntityFirstBaselinePostgresTest` exercises actual EntityManager transactions over the same generated baseline. DATE, FLOATING, UTC and ZONED representations, scope separation, invalid/mixed temporal shapes, missing zones, attendee requirements, ordinal bounds and recurrence rules are verified. A second initialization applies no changes and retains existing rows. Reflection is confined to test fixtures that deliberately bypass domain validation; there is no reflection-based production persistence.

These scenarios establish bounded baseline/ORM contracts, not full recovery, complete schema parity, native/ARM64 execution, API integration or live collaboration. Exact-source CI results, not the presence of test code, establish which checks passed.

## Integrity is more than mapping validation

Hibernate validate does not establish every physical constraint or index. The Files active-path CHECK now explicitly requires `active_path_key IS NOT NULL` for ACTIVE rows: SQL CHECK accepts UNKNOWN, so a bare equality to a nullable column could bypass both the intended invariant and ordinary unique-key protection. Positive binding revision is also declared at the entity boundary. Tombstones may still have null active keys and release the path without deleting their identity.

The full replacement still requires inventorying SQL-only constraints/defaults/indexes and runtime seed requirements across all included domains. Do not discard an integrity rule because it was written in an old SQL file. Partial-index predicates or PostgreSQL-specific semantics need actual database tests; do not silently replace them with broader uniqueness or unrelated H2 evidence.

## Initialization and authoring are different responsibilities

Target: generate the reviewed fresh-install baseline from entities; use Flyway as a small execution/history mechanism if needed. Hibernate must not alter a serving database alongside Flyway. Serving stays validation-only with DML permissions. Do not introduce automatic ddl-auto=update, a custom schema-diff framework or another schema language.

There are no released installations requiring compatibility with every development schema. Pre-release history can be replaced in a deliberate consolidation. This supersedes preserving every development migration forever, not protecting existing owner/Dogfood data.

The running initializer and existing migration resources have not been switched by these changes. ADR 0023 in the current pin still describes SQL-first authority; weave-specs#170 records the approved change. Complete generated-baseline CRUD, constraints, locks, restart/recovery and the explicit specification update before retiring the old path. Simplify marker/fingerprint compatibility code only with the corresponding safety coverage.

A future coherent cutover replaces the old chain, pin and initializer together and documents the fresh-install/reset boundary. No automatic adoption of a non-empty database, Flyway clean/repair, volume deletion or live reset is authorized. Destructive owner-data resets require explicit separate approval.

## Maintenance and consolidation

Services, controllers, entities and provider integrations belong to their domains; a shared database does not make Core their owner. The Java agent guard follows the extracted serving and persistence domain source roots and still scans future production roots. Its legacy provider-selector allowance was not enlarged to make refactoring pass.

The merged Skills work is integrated with domain/ORM guidance. Java agent gate registration lives with `coreArchitectureCi`; lazy task wiring retains both documentation and Server CI checks without duplicate registration. The developer handbook keeps its corrected Files project path; navigation, root/Server agent rules and this guide link the single shared Java standard.

Update scoped instructions, source checks, artifact-input closure and tests with source moves. Keep implementation status distinct from the final App/Core/domain and deployment targets.
