# ADR: iCal4j as the Calendar standards engine

Status: Accepted for the native-provider closure track; conformance remains gated by PR #1325.

## Context

Weave owns the canonical Calendar domain, authorization, persistence, lifecycle, synchronization and CalDAV facade. It must not implement a second handwritten RFC 5545 recurrence/parser engine.

## Decision

A pinned iCal4j 4.x dependency owns iCalendar syntax and recurrence semantics behind two Weave ports:

- `IcalendarCodec` for iCalendar ↔ canonical Weave values;
- `RecurrenceEngine` for bounded occurrence calculation.

No iCal4j model type may cross into canonical domain records, JPA entities, application/provider ports or controllers.

Canonical time semantics remain explicit:

- DATE → `LocalDate`, with exclusive DTEND;
- FLOATING → `LocalDateTime` without implicit zone;
- UTC → `Instant`;
- ZONED → local date-time plus IANA TZID.

RDATE, EXDATE and RECURRENCE-ID retain matching temporal semantics. Server-side range/free-busy evaluation of DATE/FLOATING values requires an explicit evaluation zone and does not mutate stored serialization semantics.

## Supported recurrence profile

The product profile supports DAILY, WEEKLY, MONTHLY and YEARLY plus INTERVAL, COUNT, UNTIL, BYDAY, BYMONTHDAY, BYMONTH, BYSETPOS, WKST, RDATE, EXDATE and single-instance RECURRENCE-ID moves/cancellations. Unsupported constructs fail atomically rather than being silently downgraded.

## Transitional marker

The normalized relational Calendar tables exist, but any remaining compact legacy projection in `weave_calendar_events` is **not canonical authority**. PR #1325 must either remove that projection or prove that production reads/writes use normalized temporal/attendee/recurrence/override state and document a bounded removal criterion. This marker must not survive final closure without that proof.
