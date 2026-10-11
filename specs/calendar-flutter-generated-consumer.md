# Flutter Calendar generated User consumer

Implementation evidence for #1473/#1479 against the pinned consolidation and
Calendar domain contracts. This changes the Flutter consumer only; Server owns
all routes, transport DTOs, authorization, binding and temporal semantics.

- Discover authorized calendars through the generated User client before querying
  their opaque references. Use the member profile's explicit IANA evaluation zone.
  Provider paths, credentials, UIDs and northbound CalDAV are absent from member UX.
- Retain generated complete event content and strong versions in a session-bound
  in-memory snapshot. Update preserves unsupported editor fields, including attendee
  values, and preserves DATE/FLOATING/UTC/ZONED intent. DATE ends remain exclusive.
- Master and editor wall-clock fields use zone-neutral carriers so the host's DST
  gaps cannot normalize a FLOATING/ZONED/UTC value. Day/week/month display uses
  half-open interval overlap, including multi-day events and exclusive DATE ends.
- Agenda occurrence times are display projections, never replacement DTSTARTs.
  Recurring series/exception editing and deletion are visibly guarded until the
  editor can explicitly distinguish series and occurrence changes. Reading and
  display remain available with localized explanation.
- Create has a stable idempotency key across an authentication retry. Reads and
  writes verify the same server-confirmed member/organization across refresh and
  reject late results after session/server changes. Presentation operations also
  fence their session/view generation, so failed old mutations cannot restore old
  member or scope data. An open draft cannot cross a member session change. Writes use current If-Match;
  412 conflicts require reload/review and never silently overwrite another change.
- Member Calendar shares Weave sign-in. Remove external calendar credential/setup
  UI. Preserve capability recovery, accessible controls and localized failure states.

Validation: generated transport wire assertions, temporal preservation, session
switches, retry identity, 412 behavior, widget permission/recurrence guards and
existing accessibility/recovery tests. Run client generation, analysis and focused
Flutter tests. These fixtures do not establish live integrated E2E acceptance.
