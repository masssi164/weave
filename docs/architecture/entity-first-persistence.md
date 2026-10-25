# Entity-first persistence

Owner-approved direction, 2026-10-25. Implementation: #1745 / #1746. Normative reconciliation and runtime cutover: [weave-specs#170](https://github.com/masssi164/weave-specs/issues/170).

## Authoring contract

JPA entities in the owning domain are the leading model for ORM-expressible persistence. Use Spring Data derived repositories, typed projections, JPQL/Criteria, entity graphs and declarative transactions for ordinary reads and writes. Do not add parallel handwritten SQL CRUD or maintain table definitions separately from the entity model. Provider-neutral domain records and API DTOs remain separate where their actual invariants/contracts differ; do not return managed entities over the API.

Model constraints, indexes, relations and versioning explicitly in the mappings. ORM does not remove the database or the SQL it executes: it removes duplicated manual authoring. An unavoidable database-specific operational statement is a narrowly documented exception, not a second application persistence layer. Prefer records and selective Lombok for boilerplate; never generate credential-bearing toString or entity equality blindly.

## Implemented: offline ORM schema export

```sh
./gradlew :server:entitySchemaCheck
./gradlew :server:entitySchemaExport
```

The exporter uses Spring's managed-type discovery and Boot naming defaults, then the standard JPA schema-generation API with the pinned Hibernate PostgreSQL dialect. Its input is the **actual compiled runtime classpath**, not a second list of entity names or tables. Export order is canonicalized. The current application is still the full transitional Server; this is not yet proof of production `-Pdomains` assembly.

Output: `server/build/generated/entity-schema/V1__entity_baseline.sql`. This is generated code and a **candidate** baseline, not a script to edit by hand or automatically apply. The task has real compilation/resource prerequisites and never starts the web application, provider clients, schedulers or a database. Database actions are disabled, metadata connections are disabled, and a rejecting DataSource prevents accidental connections. No environment credentials are consumed. Tests use real Hibernate generation rather than a recording mock.

The exporter is in the `schemaExport` source set; it is not packaged into the serving bootJar or native executable. The root architecture gate runs the candidate export and isolated contract tests. Schema generation remains distinct from OpenAPI generation, which still follows the compiled controller/handler graph.

## Initial schema and deployment are different responsibilities

Target: generate the reviewed fresh-install baseline from the entity model; retain Flyway only as a small execution/history mechanism if needed. Hibernate must not alter a serving database alongside Flyway. Serving stays validation-only with DML permissions. Do not introduce automatic ddl-auto=update, a home-grown schema diff engine, or a new schema-definition language.

Weave has no released user installations requiring compatibility with every previous development schema. The pre-release history may therefore be replaced as part of a deliberate baseline consolidation. This supersedes an unconditional requirement to preserve every development migration forever, **not** the requirement to protect existing owner/Dogfood data.

## Cutover still required

The running schema initializer and its existing migration resources are **not switched by this export slice**. The pinned ADR 0023 still describes SQL-first authority; weave-specs#170 records the explicit owner-approved change instead of silently editing the lock.

Before replacing that path: complete entity mappings for SQL-only constraints/defaults/indexes, execute the generated baseline against disposable PostgreSQL, test actual CRUD, tenant constraints, locks, restart and recovery, and simplify the old marker/fingerprint compatibility apparatus against those checks. Hibernate validate alone does not verify every physical database guarantee. Do not discard an integrity rule just because it was previously expressed in SQL.

Then replace the unused pre-release migration chain in one coherent change, update the specification pin and tests, and document a fresh-install/reset boundary. No automatic adoption of an existing schema, Flyway clean/repair, deleted persistent volumes, or live reset follows from this change. Any destructive owner-data reset requires separate explicit authorization.

Services, controllers, entities and provider integration remain owned by their domains. Core is not the owner of all persistence just because the domains share a database.
