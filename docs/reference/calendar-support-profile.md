# Calendar support profile

This profile records the Calendar compatibility boundary inherited from PR #1325 and the current #1470/#1479 release overlay. A capability is not considered complete merely because its domain type exists; the indicated server path and qualification evidence must also be green. The release northbound product contract is the code-first Calendar User API under `/api/calendar/calendars`; generated-consumer and real runtime evidence remain required for release acceptance. See `specs/calendar-user-api-contract.md` for implementation evidence and limitations.

## iCalendar / VEVENT

| Capability | Status | Notes |
| --- | --- | --- |
| VEVENT | Supported | Canonical Calendar event type. |
| DATE | Supported | `LocalDate`; DTEND is exclusive. |
| FLOATING | Supported | `LocalDateTime`; no implicit UTC conversion. |
| UTC | Supported | `Instant`. |
| TZID/ZONED | Supported | Local wall clock plus IANA TZID. |
| RRULE DAILY/WEEKLY/MONTHLY/YEARLY | Supported | Delegated to iCal4j. |
| INTERVAL / COUNT / UNTIL | Supported | COUNT and UNTIL are mutually exclusive in the Weave profile. |
| BYDAY / BYMONTHDAY / BYMONTH / BYSETPOS / WKST | Supported | Delegated to iCal4j recurrence evaluation. |
| RDATE / EXDATE | Supported | Temporal kind must match DTSTART. |
| RECURRENCE-ID single-instance move/cancel | Supported by canonical model | Final closure requires normalized relational persistence/interoperability evidence. |
| RANGE=THISANDFUTURE | Unsupported | Rejected; no silent downgrade. |
| VTODO / VJOURNAL | Unsupported | Outside current product profile. |
| Unknown properties, parameters, components and alarms | Guarded / rejected | Unsupported input fails closed before editing. Unknown X-* and VTIMEZONE components are not silently discarded. Only explicit IANA TZID semantics are supported. The legacy owned X-WEAVE scope fields cannot override authorized server scope. |

## Southbound CalDAV and historical protocol evidence

Public northbound CalDAV, including its legacy discovery/facade routes, is deferred by the current release profile. The following rows describe adapter or historical protocol implementation evidence; they do not make northbound CalDAV a #1470 closure requirement or claim general CalDAV conformance. Southbound objects whose href identity differs from their UID fail closed until a lossless mapping contract exists.

| Capability | Status | Notes |
| --- | --- | --- |
| Collection discovery / PROPFIND | Historical facade evidence; northbound deferred | Provider-specific availability remains readiness-gated. |
| calendar-query REPORT | Supported | Time-range requests are bounded. |
| sync-collection | Supported | Native sync must use captured logical high-water. |
| GET / PUT / DELETE | Supported | ETag/precondition behavior is part of closure evidence. |
| calendar-multiget | Guarded | Must be covered before full CalDAV conformance is claimed. |
| free-busy | Supported application behavior | DATE/FLOATING evaluation uses explicit evaluation zone. |
| MKCALENDAR | Guarded / provider dependent | No implicit provisioning fallback. |
| Scheduling / federation | Unsupported | Not part of this closure. |

## Limits

Recurrence evaluation is request-window bounded and result bounded. Oversized iCalendar input, excessive components/properties, unsupported recurrence grammar and invalid timezone data fail atomically with support-safe errors.

## Closure marker

The profile may only be changed from Guarded to Supported when committed tests/evidence prove the behavior. The final PR must not retain claims that rely only on legacy compact Calendar projection fields.
