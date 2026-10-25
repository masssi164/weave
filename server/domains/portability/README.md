# Portability and transfer runtime ownership

Owns the existing migration dry-run/apply-gate orchestration and HTTP/wire boundary, durable
migration evidence adapter and port, canonical resumable transfer adapter, pure transfer/evidence
values, JPA mappings, constraints/repositories, typed settings and contract resources. Transfer and
portability JPA mappings are consolidated under `portability.persistence`, preserving package-private
access, existing table identities, optimistic versions and locking. The model library remains pure.
Serving imports are separate from reusable persistence imports; schema/operator discovery does not
construct HTTP/provider graphs. Ordinary product persistence uses JPA, with no parallel JDBC CRUD.

The former App migration services/controllers/values and transfer adapter are deleted at their old
locations. Core Operations owns the shared stable idempotency-key formatter, independently used by
Integrations and migration planning; Portability does not depend on another future implementation.
The unused package-private evidence approval-copy helper was retired after verifying no callers;
actual approval remains guarded by the existing use case and audit/evidence requirements. No applied
schema history, owner data or provider migration behavior is reset. Dry-run estimates and blocked
Files migration are preserved; source moves do not qualify production portability.

Contract resources and test-only file/memory evidence fixtures move with this owner. The file fixture
is not a production fallback. App retains PostgreSQL/backup integration harnesses in the owning
persistence namespace until application test composition is finished.

This is a real Gradle project with module-local `AutoConfiguration.imports`; it has no App dependency.
Final release exclusion requires selected App composition, which remains guarded during extraction.

From repository root:

```sh
./gradlew :server:domains:portability:check
```

Owning units and actual Boot discovery run in separate `test` and `contextTest` lanes. Tests supply
real shared ORM prerequisites, inspect actual handler mappings, prove sibling exclusion and non-web
serving absence. Combined API, authorization, PostgreSQL and application gates remain required.
