# Calendar domain

## Current ownership

- `:server:domains:calendar:model` owns provider-neutral Calendar values and exact DATE, FLOATING, UTC and ZONED semantics. It has no production dependency on Core, Spring, persistence or iCal4j.
- `:server:domains:calendar:persistence` owns the native and normalized JPA mappings, embedded IDs and Spring Data interfaces. It depends on Calendar's model, not the generic persistence module or Files.

The source packages and SQL mappings are retained to keep existing Server consumers compatible. All historical Flyway migrations remain at their existing authoritative location. Moving an entity does not move schema authority or authorize deleting stored calendar data.

## Still to extract

Authenticated Calendar services, the native provider adapter, CalDAV/iCalendar integration, HTTP controllers and module-local Boot auto-configuration remain in the transitional executable Server. These library projects are not a completed Calendar-only executable.

## Verification

From the repository root:

```sh
./gradlew :server:domains:calendar:check
./gradlew coreArchitectureCi
```

Domain checks cover pure model dependencies, temporal semantics, entity ownership and persistence boundaries. Existing complete Server/PostgreSQL/API tests remain required. See `docs/architecture/server-domain-ownership.md` for assembly, OpenAPI ordering and the remaining phases.

## Cleanup record

`CalendarDomain.java` was removed from `weave-application-core`; it is Calendar behavior, not a shared platform primitive. `CalendarNativePersistence.java` and `CalendarNormalizedPersistence.java` were removed from `weave-persistence-jpa`; Calendar owns its mappings. Keep only the domain-owned source copies. Retained legacy constructors and schema mappings are not dead code merely because their comments describe compatibility.

## Normalized payload persistence extraction

`NativeCalendarRelationalStore` moved from the executable's Calendar adapter package into this
domain's persistence library. Its production dependency is the owning compiled JPA graph, not
Server services or iCal4j. Ordinary payload CRUD and candidate selection now use JPA with scoped
composite IDs. The redundant unused recurrence implementation is removed; active recurrence
projection stays in `CalendarOccurrenceEngine`. Ranged native queries select candidate IDs in
the database before loading/enriching payloads.

The isolated store unit test moved with production code. PostgreSQL adapter, HTTP preview and
generated-baseline checks remain in the existing integrated harness until its separate
extraction. No schema/history or provider behavior changes and no persistent data reset occur.
The remaining Calendar runtime/API extraction is still open.
