# MCP Calendar through the generated User API

Status: implementation contract for the bounded read/write catalog of #1479 under
#1470. Product and security authority are the current pinned Calendar domain,
accepted consolidation steering, and workload-token acceptance examples. This document records
repository conformance and does not define a second HTTP schema.

The curated `calendar.agenda` tool uses the generated JVM `CalendarUserApi`
`listUserCalendars` and `queryCalendarAgenda` operations. Its stable MCP input
is a Weave Calendar reference, a finite RFC 3339 interval no longer than 366
days, and an IANA evaluation time zone. The adapter projects only bounded
event/occurrence data from generated transport models; it retains the
materialized Event ID versus transient preview-handle distinction. It fails
closed on an oversized result instead of returning an apparently complete
truncated agenda. Browsing does not materialize an Event or create a mapping.

The MCP edge requests only the admitted `calendar.read` or `calendar.write`
scope for the selected tool in its Standard Token Exchange V2 request. The
Server resolves the protected member binding, rechecks current Keycloak entitlement,
organization membership, the required member capability, and Space `VIEW` or
`EDIT` access, and verifies the active Calendar provider binding. It never uses
the workload subject as the member identity or forwards either bearer to a
provider. Human User tokens retain their existing admission path.

The deployment MCP scope ceiling and Keycloak workload registration allow
`calendar.read` and `calendar.write` alongside the existing Files scope. The
edge narrows each tool invocation to its own admitted domain scope before
exchange. The protected binding permits only its curated tool classes, while
the current token, member entitlement, Space grant, and active
binding remain mandatory for each request.

Verification must include actual generated-client HTTP calls, wrong-scope and
cross-organization denial, revoked-member and stale-binding denial before
provider content, a current-member success case, and a real supported
OpenClaw/Weaver invocation. Unit tests and a protocol double alone do not
complete #1479. Calendar MCP writes also use the generated User API and remain
subject to the established OpenClaw approval interaction, current member
capability, and User API mutation preconditions.

## Current-release binding configuration

The accepted #1470 contract permits a pre-issued short-lived workload bearer and
does not require an ARC Cell. When Server is configured with the absolute
`WEAVE_MCP_RELEASE_BINDING_FILE` path, that private version-1 binding file is
the sole MCP workload-to-member authority. It is reread for every operation;
an absent, revoked, expired, malformed, or unreadable binding fails closed and
does not fall back to a Cell. The file belongs only to Server, uses mode `0600`,
and may contain only workload/member references and the bounded tool-class
grant, never bearer values or provider/IAM credentials. Existing Keycloak
organization/Weaver entitlement observation, exact exchanged-token scope,
Calendar owner/admin capability, Space access, and audit remain mandatory.
An existing-policy `weaver-cell-*` Keycloak client may be registered for this
bounded binding without provisioning a Cell; the client name does not grant
Cell authority. A separately registered `weaver-mcp-*` client is also valid.
The accepted corpus release steering and Calendar domain spec govern semantics;
this paragraph records the repo-owned configuration and proof interface.
For a supported OpenClaw invocation, `WEAVE_MCP_WORKLOAD_TOKEN` is an ephemeral,
process-local secret input to its MCP Authorization header. It contains the
short-lived workload token only, never a member, provider, or IAM-admin token;
rotation and reconnection are required before its 60-second expiry. This
variable is not a Server or MCP-edge setting.
