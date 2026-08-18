# Database migrations

Weave applies backend-owned PostgreSQL schema changes with Flyway before the backend is allowed to start.

## Deployment order

The deployment contract is:

```text
postgres provision/reconcile
    -> schema-init
        -> schema-receipt-check
            -> backend (Hibernate validate)
```

`schema-init` is a one-shot process. A failed migration blocks the backend instead of allowing Hibernate to repair the database implicitly.

## Migration files

Committed migrations live in:

```text
server/src/main/resources/db/migration/
```

Use monotonically increasing Flyway versions and descriptive names, for example:

```text
V1__native_schema_baseline.sql
V2__normalize_calendar_recurrence.sql
V3__normalize_matrix_routing_state.sql
```

After persistent dogfood accepts a migration, do not edit it. Add a new forward migration.

## Backup and upgrade

Before applying a migration to persistent state:

1. obtain a database backup using the deployment's normal backup path;
2. record the candidate commit and current Flyway schema-history version;
3. run `schema-init` against the target database;
4. require the schema receipt and Hibernate validation to succeed;
5. start the backend only after the receipt check passes.

Application rollback does not roll the database backwards automatically. Database changes must remain compatible with the supported rollback window or be corrected by a new forward migration.

## Non-empty schemas and baselines

Weave does not silently enable `baselineOnMigrate`. A non-empty database with no expected Flyway history is an operator-visible blocked state. Baseline adoption, if ever required, needs an explicit procedure with a verified expected schema fingerprint and a backup.

## Development and tests

Concurrency, locks, indexes, JSON behavior, migration checksums and sync-ordering tests use PostgreSQL/Testcontainers. H2 may be used only for fast tests whose assertions are database-neutral.

## Forbidden production behavior

- Hibernate `ddl-auto=update`, `create` or `create-drop`;
- Flyway `clean`;
- editing an already accepted migration;
- hidden migration of Nextcloud, Synapse, MAS or other provider-owned data;
- dual-write compatibility paths used as a substitute for an explicit migration.
