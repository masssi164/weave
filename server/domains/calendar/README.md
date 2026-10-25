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
