# Native schema migration closure evidence

Status: qualification in progress on PR #1325. This document becomes final only after the PostgreSQL/Flyway closure gate and regular CI are green on the same final head.

## Authority

- Flyway SQL migrations are the production schema authority.
- Hibernate validates mappings; it does not create or update production schema.
- PostgreSQL is authoritative for constraints, indexes, locking and concurrency.
- H2 remains limited to database-neutral fast tests.
- `baselineOnMigrate` is not silently enabled.
- Flyway `clean` is disabled for production-capable profiles.
- no external-provider content migration or dual-write compatibility path is part of schema initialization.

## Required qualification

- clean PostgreSQL migration from V1 through the current version;
- application compile/start path compatible with Hibernate validation;
- prior-version to current-version upgrade;
- concurrent migration/startup safety;
- checksum validation of applied migrations;
- required constraints/indexes present;
- Matrix snapshot table absent after migration;
- Calendar/Chat normalized tables present;
- backup/restore procedure documented and followed by validation.

## CI gate

`.github/workflows/native-persistence-closure.yml` is read-only with respect to repository contents and runs against PostgreSQL 16.9.

## Final run references

- final PR head: pending
- Native Persistence Closure: pending
- regular CI: pending
