# Native Calendar provider

## Boundary

Calendar is a provider-neutral canonical domain. CalDAV/iCalendar is a permanent northbound Weave Server surface. Provider selection happens only behind `CalendarProviderPort`.

```text
CalDAV/iCalendar
  -> canonical Calendar application/domain
    -> CalendarProviderPort
      -> weave-native (selected default)
      -> optional CalDAV/Nextcloud/Radicale providers
```

Changing the selected provider must not change canonical event IDs, authorization semantics, northbound URLs, ETags/precondition behavior or sync contracts.

## iCal4j responsibility

The native provider delegates RFC 5545 syntax and recurrence semantics to a pinned iCal4j 4.x adapter. iCal4j owns iCalendar parsing/serialization, escaping/folding, property/parameter grammar, RRULE/RDATE/EXDATE, RECURRENCE-ID and timezone/VTIMEZONE handling. No iCal4j types enter JPA entities, canonical domain values, application/provider ports or public controllers.

Canonical Calendar explicitly represents:

- DATE as `LocalDate`;
- FLOATING as `LocalDateTime` without zone/offset;
- UTC as `Instant`;
- ZONED as local date-time plus IANA TZID.

Floating values are never silently converted to UTC. DATE `DTEND` is exclusive. RDATE, EXDATE and RECURRENCE-ID retain compatible temporal semantics.

## Native persistence

The native provider normalizes event temporals, attendees, recurrence rules/dates, overrides, timezone definitions, extension properties, sync heads and change rows in PostgreSQL. A serialized ICS document is not a second canonical source of truth.

## Recurrence and windowing

The canonical domain contains recurrence values but no recurrence engine. Occurrence calculation is delegated to `RecurrenceEngine` backed by iCal4j and always receives a bounded window/result limit.

PostgreSQL selects candidates before recurrence expansion. Moved overrides whose resulting interval intersects the requested window are selected independently of the master occurrence window.

## Synchronization

Calendar mutations use explicit logical revisions. A per-calendar/scope sync head is locked and advanced in the same transaction as the canonical mutation and change row. A sync reader captures snapshot high-water `H` and returns only revisions in `after < revision <= H`; continuation tokens remain bound to that snapshot.

## Fresh-start policy

No Nextcloud/Radicale content import, dual write, compatibility reader or hidden adoption job is introduced. Optional providers remain selectable behind the same canonical port.