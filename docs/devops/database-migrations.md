# Database migrations

Weave applies backend-owned PostgreSQL schema changes with Flyway before the backend is allowed to start.

## Deployment order

The deployment contract is:

```text
postgres provision/reconcile + native Files volume initialization
    -> schema-init (relational history + Files volume row/marker authority)
        -> schema-receipt-check
            -> backend (Hibernate validate)
```

`schema-init` is a one-shot process. A failed migration blocks the backend instead of allowing Hibernate to repair the database implicitly.

Only the PostgreSQL role-reconciliation one-shot and `schema-init` receive the private
`backend-migrator-db-password` SecretRef: reconciliation creates or rotates the role, and
`schema-init` connects as that configurable migrator, which owns the backend database, schema,
Flyway history, and migration objects. No long-running service receives that credential. The
long-running backend receives only `backend-db-password` and connects as a non-owner serving role.
A post-Flyway reconciliation grants bounded application DML and sequence use while leaving Flyway
history, schema authority, and Files volume authority read-only. Missing, shared, or
over-privileged role configuration blocks initialization.

The V7 transition additionally requires the private native Files blob root to be empty before V7
is first applied. V7 creates the authority table but never infers a generation from that empty
path. Only the existing isolated first-provision path or the same exact dogfood reset invocation
that recreated its session volumes writes a private one-shot transition context. While holding the
schema acceptance lock, `schema-init` validates empty relational/physical Files state, mints the
row and immutable marker, and emits schema receipt v6 binding their digests. Routine `up`, normal
initialization and serving cannot create or repair the pair.

The one-shot transition context and schema receipt use bounded reads, private permissions,
exclusive temporary files, atomic replacement, and file/directory durability barriers. This keeps
the accepted call resumable across a host crash between relational-row and root-marker publication
without turning retained evidence into a reusable reset capability.

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
