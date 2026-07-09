# ADR-005: Files WebDAV facade slice

Status: accepted for the first Files data-plane transition slice; WebDAV write MVP promoted by #1007

## Context

The Files domain remains Weave-owned product truth. The pinned Files corpus
requires provider-neutral file/folder objects, shares, versions, export/delete,
permission impact, fail-closed unsafe behavior, support-safe diagnostics, and no
provider-shaped member UX. ADR-004 keeps server OpenAPI as the authority for
generated Flutter, Admin Console, and MCP control-plane contracts. Massimo's
2026-07-05 correction makes the Files WebDAV facade the durable northbound
Files data plane. The OpenAPI Files member data-plane routes are removed from
the member contract; OpenAPI remains the control/discovery/readiness/revoke and
generated-model surface.

This ADR records the implementation decisions for the first northbound
`/dav/files/**` projection. It is not a new product-domain specification.

External reference check, 2026-07-04:

- Apache Jackrabbit WebDAV provides Java interfaces and utilities for building
  WebDAV servers or clients and integrates RFC 4918 plus related WebDAV RFCs.
- Jackrabbit is under the Apache License 2.0, which is compatible with Weave's
  dependency posture in principle, but still adds library behavior and
  transitive dependency review.
- `webdav_client` on pub.dev is BSD-3-Clause and supports common WebDAV methods,
  but the current package page shows version `1.2.2`, published two years ago,
  an unverified uploader, and write/copy/delete behavior caveats in its README.

## Decision 1: Jackrabbit vs custom Spring handler

Recommendation: keep a custom minimal Spring handler for the first slice.

The current slice proves the first WebDAV projection behavior over the existing
Files facade:

- `OPTIONS`
- `PROPFIND` Depth `0` and `1`
- `GET`
- `HEAD`
- support-safe failures for unauthorized, forbidden, missing, unsupported, and
  locked/write-shaped operations
- `207 Multi-Status` XML without provider URL, raw provider path, secret, token,
  or downstream payload leakage

Jackrabbit is the right candidate once Weave needs deeper RFC coverage such as
full property models, locks, write/copy/move semantics, access control
extensions, or broader compatibility testing against generic DAV clients. It is
not necessary for the current focused projection. Adding it now would increase
surface area before Weave has settled credential lifecycle, provider mapping,
and lock/copy/move behavior.

Tradeoffs:

- Custom handler is smaller, reviewable, easier to bind to Weave policy and
  support-safe error semantics, and avoids a new dependency in this PR.
- Custom handler must stay deliberately narrow; it must not grow into an
  incomplete general WebDAV stack.
- Jackrabbit has better standards breadth and long-term server/client utility,
  but adopting it should happen with an explicit dependency/license/security
  review and compatibility test matrix.

Therefore this slice keeps the custom handler. A later write/native-client PR
may adopt Jackrabbit if the evidence shows that the added complexity buys real
compatibility or correctness.

## Decision 2: internal Dart adapter vs `webdav_client`

Recommendation: use an internal Flutter Files WebDAV adapter for now.

The first Flutter path moves list/read behavior to Weave-owned `/dav/files/**`,
while OpenAPI remains responsible for discovery, status, grants, readiness,
revoke paths, generated models, and MCP allowlists. A small internal adapter
keeps request construction, bearer-token handling, XML parsing, and failure
mapping under test in the Files repository without taking a package dependency
that also exposes write helpers before Weave write semantics are ready.

Tradeoffs:

- Internal adapter is more testable against Weave fixture XML and support-safe
  failures, and it can be shared conceptually with MCP/client contract tests
  without making raw DAV a public tool surface.
- Internal adapter means Weave owns a little protocol parsing. Keep it focused
  on the subset covered by server contract tests.
- `webdav_client` already implements common WebDAV operations and may become
  useful for broad generic-client compatibility experiments.
- `webdav_client` also carries maturity and security-review risk: stale publish
  date, unverified uploader, extra dependencies, and write/copy/delete API
  surface that does not match this read-only slice.

Therefore this slice uses internal client code for the implemented list/read
data plane. MCP remains semantic: tools use Weave Files capabilities such as
list/read/search metadata through the WebDAV-backed Weave Files
facade/projection, not raw provider APIs or unrestricted protocol scripting.

## Decision 3: bearer-only vs device credentials

Recommendation: first-party Weave app uses OIDC bearer tokens now; generic DAV
clients require scoped per-device credentials later.

First-party Flutter and Web clients are authenticated Weave clients. They can
call `/dav/files/**` with the normal OIDC access token and the server enforces
Weave capability, Space/context authorization, audit, readiness, and support-safe
failures. This keeps identity and revocation aligned with the IDM session for
the member app.

Generic DAV clients are different. Many use Basic authentication, app-password
style credentials, or long-lived saved secrets. Weave must never expose provider
credentials for those clients. Generic access should use Weave-issued,
per-device, scoped credentials over TLS, with admin/member revoke controls,
credential rotation, audit attribution, expiry policy, and clear support-safe
diagnostics. The OpenAPI control plane should own create/list/revoke/status for
those credentials.

This slice implements bearer-only WebDAV access for first-party clients. The
generic/device credential path is contract-only until credential issuance,
revocation, audit, expiry, and policy UI are implemented.

## Decision 4: write semantics

Issue #1007 promotes the first WebDAV write MVP for `PUT`, `DELETE`, and
`MKCOL` on `/dav/files`. PR #1043 extends that normal member data plane with
`MOVE`, `COPY`, `LOCK`, and `UNLOCK`. The Files facade owns ETag generation,
`If-Match`/`If-None-Match` preconditions, support-safe conflict/precondition/
forbidden/quota/storage errors, lock conflict handling, and attempted/completed
mutation audit before provider adapters are invoked.

## Consequences

- `/dav/files/**` is a northbound Weave projection over the Files facade, not a
  raw Nextcloud or provider proxy.
- `/api/files/**`, organization manifest discovery, readiness, revoke/setup
  flows, generated models, and MCP allowlists remain OpenAPI control-plane
  surfaces; they are not the Files list/read/write data plane.
- Flutter Files lists, reads, uploads, creates folders, and deletes through
  Weave WebDAV. Native OS File Provider/DocumentsProvider integrations remain
  separate setup/proof work.
- MCP Files tools remain semantic Weave Files tools routed through the
  WebDAV-backed facade/projection where they touch file data-plane semantics.
  Raw provider APIs and unrestricted WebDAV operations are not a public MCP
  surface.
- The write MVP PR should use `release-notes-feature` because it adds guarded
  WebDAV mutation behavior to the Files protocol data path.
