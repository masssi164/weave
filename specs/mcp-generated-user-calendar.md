# MCP Calendar through the generated User API

Status: implementation contract for the bounded read slice of #1479 under
#1470. Product and security authority are the pinned Calendar domain and
workload-token acceptance examples in the pinned `weave-specs` commit
`8c74d6d0aac045106587e25f1cddbc6cba2dbc0c`. This document records
repository conformance and does not define a second HTTP schema.

The curated `calendar.agenda` tool uses the generated JVM `CalendarUserApi`
`listUserCalendars` and `queryCalendarAgenda` operations. Its stable MCP input
is a Weave Calendar reference, a finite RFC 3339 interval no longer than 366
days, and an IANA evaluation time zone. The adapter projects only bounded
event/occurrence data from generated transport models; it retains the
materialized Event ID versus transient preview-handle distinction. It fails
closed on an oversized result instead of returning an apparently complete
truncated agenda. Browsing does not materialize an Event or create a mapping.

The MCP edge requests only the admitted `calendar.read` scope in its Standard
Token Exchange V2 request. The API accepts that exchanged workload token for
Calendar GET operations only and rejects it for mutations. The Server resolves
the signed profile's member binding, rechecks current Keycloak entitlement,
organization membership, a current role granting the same `calendar.read`
capability as the User API, and Space `VIEW` access, and verifies the active
Calendar provider binding before content leaves the boundary. It never uses
the workload subject as the member identity or forwards either bearer to a
provider. Human User tokens retain their existing admission path.

The deployment MCP scope ceiling and Keycloak workload registration allow
`calendar.read` alongside the existing Files scope. A cell requests only the
domain scopes it is granted; the edge narrows each tool invocation to its own
domain before exchange. The signed runtime policy permits both curated read
classes, while the current token, member entitlement, Space grant, and active
binding remain mandatory for each request.

Verification must include actual generated-client HTTP calls, wrong-scope and
cross-organization denial, revoked-member and stale-binding denial before
provider content, a current-member success case, and a real supported
OpenClaw/Weaver invocation. Unit tests and a protocol double alone do not
complete #1479. Calendar MCP writes are a separate later slice subject to
the established approval interaction and User API mutation preconditions.
