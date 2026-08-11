# Native Calendar closure evidence

Status: qualification in progress on PR #1325. Final closure evidence is recorded only on the final green PR head.

## Architecture under test

- canonical Calendar remains behind `CalendarProviderPort`;
- `weave-native` is the default provider implementation;
- iCal4j is the only iCalendar parsing/serialization and recurrence engine boundary;
- DATE, FLOATING, UTC and ZONED values remain semantically distinct;
- normalized PostgreSQL tables own attendees, recurrence values, overrides, temporal values and timezone definitions;
- SQL candidate selection occurs before bounded recurrence expansion;
- CalDAV remains a permanent northbound standards surface;
- the optional CalDAV provider adapter remains southbound and replaceable.

## Required evidence

- DATE/FLOATING/UTC/ZONED round trips;
- DST gap and overlap fixtures;
- MONTHLY/YEARLY and BY* recurrence profile;
- RDATE/EXDATE and moved/cancelled RECURRENCE-ID overrides;
- standard-compliant VCALENDAR/VEVENT parsing and serialization;
- CalDAV query, multiget/sync, GET/PUT/DELETE, ETags and preconditions;
- PostgreSQL restart/concurrency and snapshot-high-water behavior;
- no hand-written RRULE/iCalendar parser remains as a second standards authority.

## Commands

```bash
./gradlew :server:test --tests 'com.massimotter.weave.backend.service.calendar.*' --tests 'com.massimotter.weave.backend.calendar.*'
./gradlew :server:compileJava :server:compileTestJava
./gradlew serverCi
```

## Final run references

- final PR head: pending
- Native Provider Gate: pending
- Native Persistence Closure: pending
- regular CI: pending
