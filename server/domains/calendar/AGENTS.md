# Calendar domain rules

Use the root and Server agent rules. Keep code, comments and documentation in English.

- Model code is Java-only and provider-neutral. Do not add Spring, JPA, Jackson or iCal4j dependencies to `model`.
- Calendar JPA types belong in `persistence`, never shared Core or the generic root persistence project.
- Entity-first authoring is the owner-approved direction: mappings own ORM-expressible structure and invariants. Follow `docs/architecture/entity-first-persistence.md` and weave-specs#170. The legacy initializer is still active until the complete cutover; do not confuse that transitional fact with a permanent SQL-first design rule.
- Provider-specific parsing/serialization stays behind Calendar ports. Preserve DATE/FLOATING/UTC/ZONED, recurrence, authorization and binding revision semantics.
- Qualify generated baselines with actual PostgreSQL ORM writes, including invalid temporal shapes, recurrence combinations and scoped identities. Mapping validation alone does not prove constraints or indexes.
- Runtime serving beans register through this module's actual servlet-only `AutoConfiguration.imports`; persistence owns its separate mapped-graph registration. Do not add App scanning, duplicate registrations or fake auto-configuration to the pure model.
- Never resolve circular dependencies by making Calendar depend on the executable Server or another domain's JPA repositories.
- Record removed source/build paths, replacement and reason in the domain README and commit. Delete redundant implementations rather than commenting them out; retain planned product functions and owner data.

Run `./gradlew :server:domains:calendar:check` and `./gradlew :server:app:postgresJpaTest --tests '*CalendarEntityFirstBaselinePostgresTest'` from repository root. Generated-baseline integration tests temporarily use the existing Server test harness; that is not a production reverse dependency. Actual controllers/services/providers/settings and owning tests now live in this domain. Reduced executable composition remains open in #1745; do not remove its packaging/export guard before real selected assembly. Owning tests must not depend on App; retain coupled integration fixtures in the existing App harness.
