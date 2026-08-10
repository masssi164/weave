# Database schema authority

Weave-owned relational persistence has exactly one production schema authority: versioned Flyway SQL migrations.

## Boundary

The canonical Files, Calendar, Chat, Identity and platform domains remain provider-neutral. Their selected `weave-native` implementations may persist through JPA/PostgreSQL, but neither Hibernate nor the provider implementation defines the product domain.

```text
canonical domain
    -> provider/application port
        -> weave-native adapter
            -> JPA mapping
                -> PostgreSQL schema
                    <- Flyway versioned SQL authority
```

External provider databases such as Nextcloud, Synapse or MAS are outside this schema authority. No Flyway migration in the Weave backend imports or rewrites those provider databases.

## Production lifecycle

1. PostgreSQL database and application role are provisioned.
2. The `schema-init` one-shot process acquires Flyway's PostgreSQL migration lock and applies committed migrations.
3. The migration history/checksums are validated.
4. `schema-init` writes support-safe completion evidence.
5. The backend starts with `spring.jpa.hibernate.ddl-auto=validate`.
6. Hibernate may reject a mapping/schema mismatch but must not create, update or repair production objects.

The normal backend process does not own schema evolution. Deployments therefore fail closed before application traffic when migration or validation fails.

## Migration policy

Migration files under `server/src/main/resources/db/migration` are immutable once accepted for persistent dogfood. Changes are forward-only. Destructive evolution uses explicit expansion/contraction migrations and restart-safe data backfills where required.

`baselineOnMigrate` is not enabled automatically. A non-empty schema without the expected Flyway history is rejected and requires an explicit, separately evidenced operator procedure. Flyway `clean` is not part of any production-capable runtime path.

## PostgreSQL qualification

PostgreSQL is authoritative for constraints, indexes, transaction ordering, locking, concurrent migration, JSON semantics and provider sync heads. H2 remains limited to fast tests that do not make PostgreSQL-specific claims.

## Fresh-start boundary

The first Flyway baseline captures only Weave-owned relational state. It does not add legacy-provider import, dual writes, compatibility readers or hidden background adoption.
