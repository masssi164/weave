# Calendar support profile

This profile is normative for the Calendar scope of PR #1325. A capability is not considered complete merely because its domain type exists; the indicated server path and qualification evidence must also be green.

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
| Unknown X-* properties | Guarded | Must not override canonical/security fields; preservation policy requires explicit evidence before closure claim. |

## CalDAV

| Capability | Status | Notes |
| --- | --- | --- |
| Collection discovery / PROPFIND | Supported facade surface | Provider-specific availability remains readiness-gated. |
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
