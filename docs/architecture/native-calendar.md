# Native Calendar provider

## Boundary

Calendar is a provider-neutral canonical domain. CalDAV/iCalendar is a permanent northbound Weave Server surface. Provider selection happens only behind `CalendarProviderPort`.

```text
CalDAV/iCalendar
  -> canonical Calendar application/domain
    -> CalendarProviderPort                 (Provider Port)
      -> weave-native                       (Provider Adapter, selected default)
        -> IcalendarCodec / RecurrenceEngine (Infrastructure Ports)
          -> iCal4j adapter                 (Infrastructure Adapter)
            -> iCal4j

      -> optional CalDAV/Nextcloud/Radicale providers
```

The project-wide provider/infrastructure terminology is defined in [`provider-and-infrastructure-boundaries.md`](provider-and-infrastructure-boundaries.md).

Changing the selected provider must not change canonical event IDs, authorization semantics, northbound URLs, ETags/precondition behavior or sync contracts.

## iCal4j responsibility

`IcalendarCodec` and `RecurrenceEngine` are Infrastructure Ports used by the native Calendar provider. Their concrete infrastructure adapter delegates RFC 5545 syntax and recurrence semantics to pinned iCal4j 4.x.

iCal4j owns iCalendar parsing/serialization, escaping/folding, property/parameter grammar, RRULE/RDATE/EXDATE, RECURRENCE-ID and timezone/VTIMEZONE mechanics. It is not a Calendar provider and does not own authorization, lifecycle, persistence or synchronization semantics.

No iCal4j types enter JPA entities, canonical domain values, application/provider ports or public controllers.

Canonical Calendar explicitly represents:

- DATE as `LocalDate`;
- FLOATING as `LocalDateTime` without zone/offset;
- UTC as `Instant`;
- ZONED as local date-time plus IANA TZID.

Floating values are never silently converted to UTC. DATE `DTEND` is exclusive. RDATE, EXDATE and RECURRENCE-ID retain compatible temporal semantics.

## Native persistence

The native Provider Adapter normalizes event temporals, attendees, recurrence rules/dates, overrides, timezone definitions, extension properties, sync heads and change rows in PostgreSQL. A serialized ICS document is not a second canonical source of truth.

Repository interfaces form persistence Infrastructure Ports; JPA/Hibernate/PostgreSQL implementations are Infrastructure Adapters below the native Provider Adapter.

## Recurrence and windowing

The canonical domain contains recurrence values but no recurrence engine. Occurrence calculation is delegated to the `RecurrenceEngine` Infrastructure Port backed by iCal4j and always receives a bounded window/result limit.

PostgreSQL selects candidates before recurrence expansion. Moved overrides whose resulting interval intersects the requested window are selected independently of the master occurrence window.

## Synchronization

Calendar mutations use explicit logical revisions. A per-calendar/scope sync head is locked and advanced in the same transaction as the canonical mutation and change row. A sync reader captures snapshot high-water `H` and returns only revisions in `after < revision <= H`; continuation tokens remain bound to that snapshot.

## Security boundary

Access control remains a canonical Weave responsibility. iCal4j parses and projects standards data but never decides tenant scope, rights, event visibility or provider selection.

## Fresh-start policy

No Nextcloud/Radicale content import, dual write, compatibility reader or hidden adoption job is introduced. Optional providers remain selectable behind the same canonical port.
