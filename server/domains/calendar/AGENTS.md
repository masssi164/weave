# Calendar domain rules

Use the root and Server agent rules. Keep code, comments and documentation in English.

- Model code is Java-only and provider-neutral. Do not add Spring, JPA, Jackson or iCal4j dependencies to `model`.
- Calendar JPA types belong in `persistence`, never shared Core or the generic root persistence project.
- Provider-specific parsing/serialization stays behind Calendar ports. Do not change DATE/FLOATING/UTC/ZONED, recurrence, authorization or binding revision semantics during a source move.
- Preserve historical Flyway bytes and retained data. Entity annotations map tables; they are not the production schema authority.
- Runtime auto-configuration must use its own `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` after runtime components are extracted. Do not fabricate an empty registration for a Java-only library.
- Never resolve circular dependencies by making Calendar depend on the executable Server or on another domain's JPA repositories.
- Record removed source/build paths, replacement and reason in the domain README and commit. Delete redundant implementations rather than commenting them out; retain planned product functions.

Run `./gradlew :server:domains:calendar:check` from repository root. Current extraction covers model and JPA mappings only; controller/provider and optional executable composition remain open in #1745.
