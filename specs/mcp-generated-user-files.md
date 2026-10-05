# MCP Files through the generated User API

Status: implementation contract for #1473/#1474 under #1470. Product authority is
the pinned `steering/release-2026-10-product-consolidation.md` corpus file. This
document records the repository integration boundary; it does not create a second
independently authored HTTP schema.

The curated `files.search` tool and `weave://files/{fileRef}` resource use the
same generated JVM User library as product E2E. The server-owned `FilesUserApi`
`listFilesItems`, `getFilesItem`, and `downloadFilesItemContent` operations supply
all Weave HTTP paths, methods, parameters, and transport models. The MCP adapter
retains only query validation, bounded traversal, projection to its stable tool
schema, and bounded textual content handling. It does not call a northbound DAV
endpoint or parse a parallel Weave response format.

The MCP edge exchanges its request-bound workload token for the exact Weave API
audience and `files.read` scope. Its backend URI is an explicitly configured
internal `/api` base; `WEAVE_MCP_BACKEND_API_URI` replaces the DAV-specific
`WEAVE_MCP_BACKEND_FILES_URI` in all environments. The edge has no provider or
IAM administrator credential and never forwards the inbound MCP bearer.

Server admission accepts the exchanged workload identity only for read-only
User Files GET operations. It validates the exact issuer, audience, authorized
party, lifetime and scope, then resolves the current workload binding, runtime
profile, entitlement, member and Space permission on each request. A workload
token cannot create, upload, modify or delete. A normal member token continues
through the existing User admission and current organization/resource checks.
All denied paths fail before provider access and retain support-safe errors and
workload read audit evidence.

Search traverses only currently authorized Weave Files children, using stable
Weave file IDs. It matches case-insensitive names or display paths and applies
the optional path scope without treating a display path as an identity. It has
finite traversal depth and item bounds; if it cannot inspect the full requested
scope within those bounds, it fails closed rather than presenting partial results
as complete. Tool result limits remain 1–100 and do not change with the provider.
Reading resolves a stable file ID through the generated metadata operation,
requires a currently allowed download, rejects metadata above the MCP byte bound,
uses the generated binary operation, checks the actual bytes and current revision,
and removes its temporary file. Only approved textual media types become MCP
context. A mismatch or provider change rejects the read.

Verification requires generated-library compilation/freshness, exact HTTP
transport fixtures, negative workload and member authorization tests, and an
isolated product E2E run proving real `files.search` and bounded read with a
current member. These are independent semantic assertions; generated code alone
does not prove current authorization or complete retrieval.
