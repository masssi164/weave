# Files WebDAV collection synchronization

Status: implementation contract for the stacked `roadmap/files-sync-collection` slice. It is not evidence that the slice is complete or production-ready.

## Purpose

Expose canonical Files changes through the WebDAV Collection Synchronization protocol from RFC 6578 without creating a second change authority.

The accepted path is:

```text
WebDAV REPORT DAV:sync-collection
  -> bounded protocol parser and projection
    -> canonical Files synchronization query
      -> commit-ordered Files scope journal
      -> canonical Files metadata repository
```

The protocol layer owns XML, HTTP status, WebDAV properties, `href` projection, and RFC error conditions. The canonical Files application owns collection identity, tenant and Space scope, authorization inputs, revision semantics, final-state coalescing, and bounded change queries. JPA and the blob implementation remain southbound adapters.

## Normative references

- RFC 6578, Collection Synchronization for WebDAV.
- RFC 4918, HTTP Extensions for Web Distributed Authoring and Versioning.
- RFC 3253, the WebDAV REPORT and supported-report-set framework.

Implementation and tests must follow the RFC text and verified errata. Existing provider behavior is not a normative source.

## Accepted first profile

The first profile is deliberately narrow:

- `REPORT` with a `DAV:sync-collection` request body;
- canonical Files collections only;
- `DAV:sync-level` value `1` only;
- direct collection members, not recursive traversal;
- empty `DAV:sync-token` for initial synchronization;
- opaque returned tokens for incremental synchronization;
- `DAV:prop` selected-property projection through the existing WebDAV property registry;
- optional bounded `DAV:limit` using the RFC result-limit elements;
- operation-specific capability qualification for `weave-native` only.

Infinite traversal, external Files adapters, provider-native tokens, and unbounded initial enumeration are not accepted in this slice.

## Token authority

A synchronization token is an opaque application capability, not a serialized database row or a provider token.

The protected token payload binds:

- token format version;
- operation/profile identifier;
- organization;
- Space;
- canonical collection ID;
- acknowledged journal revision;
- captured high-water and continuation state when paging is active;
- journal/retention generation;
- issued-at and expiry where the selected token policy uses time bounds.

Integrity protection uses the existing Files cursor-signing authority. A second signing key or cursor format is prohibited unless the existing authority is explicitly versioned and migrated.

The request path is resolved to a canonical collection before token binding is accepted. A token issued for a collection remains bound to that canonical identity if the collection is renamed, but it is invalid when presented against another collection path or scope.

Malformed, tampered, expired, future, compacted, wrong-operation, wrong-tenant, wrong-Space, or wrong-collection tokens fail with the RFC `DAV:valid-sync-token` condition. No partial member data or token-validation oracle is returned.

## Authorization and qualification order

Before journal, provider, or blob enumeration, the Server must:

1. authenticate the caller;
2. resolve organization and Space scope;
3. authorize collection read and synchronization access;
4. validate request media type and bounded XML shape;
5. require a current operation-specific synchronization qualification for the selected adapter;
6. resolve the target as a canonical collection;
7. validate the token binding and request limits.

An absent, blocked, future-dated, expired, or adapter-mismatched qualification fails closed. Discovery advertises synchronization only while this exact qualification is current.

## Initial synchronization safety

Initial synchronization is the difficult case because a captured high-water does not by itself provide a database snapshot across multiple HTTP requests.

A multi-request initial algorithm is accepted only if it proves convergence without silent omission under concurrent create, write, move, rename, and delete. The preferred shape is:

1. capture starting journal high-water `H0`;
2. enumerate direct active members in stable canonical-ID order with an integrity-protected cursor;
3. allow duplicates but no omissions while current rows change;
4. after enumeration, replay journal changes after `H0` through a captured high-water;
5. issue the steady-state token only after every initial member or compensating journal change is guaranteed to be observable.

If the current repository cannot prove that algorithm, the first implementation must use a conservative bounded single-response initial sync. When the result does not fit, it returns the RFC limit condition and no continuation token that acknowledges unreported state.

Path ordering alone is not a safe initial cursor because MOVE can cross the cursor boundary. A stable canonical ID may be used as the enumeration key, while response ordering remains deterministic and protocol-oriented.

## Incremental synchronization

For a valid steady-state token:

1. capture a high-water revision `H` once;
2. read only committed journal changes with `tokenRevision < revision <= H`;
3. bind every continuation to the same `H`;
4. coalesce changes by final externally observable `href` state;
5. return a token that advances only through the exact emitted prefix;
6. after the final page, return a steady-state token acknowledging `H`.

Commits after `H` do not leak into the response and remain visible to the next synchronization request.

The journal remains gap-free per organization and Space. Collection projection may filter that journal, but it may not advance past an unexamined change that can affect the collection.

## Change projection

The client-visible key is `href`; the canonical identity remains internal.

For each affected direct member:

- an active final member produces the selected 200/404 property status projection used by the existing WebDAV property machinery;
- a deleted or moved-away final member produces the RFC deletion response with member `href` and HTTP 404 status;
- a move into the collection produces the active destination `href`;
- a rename within the collection produces the old deleted `href` and the active new `href`, unless the old `href` is occupied again at the captured high-water;
- delete followed by recreation at the same `href` produces the final active `href`, because WebDAV clients synchronize by resource URL;
- repeated content/property mutations produce one final active response;
- descendant-only changes are not reported as direct-member changes unless they change the direct child collection's own canonical synchronization-visible state.

Final-state lookup occurs at the captured high-water contract. If the persistence model cannot reconstruct the required state safely, the query fails closed rather than projecting current data as historical data.

Response ordering is byte-stable. Deleted and active responses for different `href` values use one documented canonical comparator, with canonical ID as the final tie-breaker where needed.

## Limits and partial responses

Bounds apply to:

- request bytes and XML depth/elements/text;
- requested property count;
- requested result limit;
- journal rows examined;
- distinct affected `href` values;
- repository lookups;
- response count and response bytes;
- token and path lengths.

The RFC limit response is used when a complete result cannot be returned within the accepted result count. A continuation token is included only when it acknowledges exactly the emitted deterministic prefix and guarantees that every remaining relevant change will be replayed. Internal effort exhaustion that cannot preserve that invariant returns a support-safe failure without an advancing token.

## Discovery

A qualified collection exposes the synchronization contract through the relevant WebDAV live properties:

- current `DAV:sync-token`;
- `DAV:sync-collection` in `DAV:supported-report-set`.

The advertisement is omitted when qualification is unavailable. Direct execution remains independently guarded; discovery is not authorization.

## Error and response contract

- successful complete synchronization uses WebDAV Multi-Status XML and includes the valid next `DAV:sync-token`;
- an RFC-defined partial/limited synchronization uses the RFC status and XML shape and includes only a safe continuation token;
- invalid tokens use `DAV:valid-sync-token`;
- unsupported traversal uses the RFC synchronization traversal condition;
- authentication and authorization use the existing WebDAV XML/error boundary;
- all protocol responses use the accepted XML media type, `Cache-Control: no-store`, deterministic namespace/prefix handling, and support-safe bodies.

Raw exceptions, SQL state, provider payloads, provider identifiers, blob bindings, credentials, and private paths are prohibited.

## Required executable evidence

### Unit and application tests

- empty, valid, malformed, tampered, expired, future, compacted, and wrong-binding tokens;
- sync-level and limit parsing;
- stable ordering and deterministic token bytes;
- write, repeated write, delete, move in, move out, rename, delete/recreate, and path-reuse coalescing;
- bounds and no-advance-on-failure behavior.

### Protocol tests

- initial and incremental `REPORT`;
- selected properties and missing-property propstats;
- deletion 404 projection;
- complete and partial limit behavior;
- invalid-token and unsupported-traversal XML;
- authentication, authorization, media type, no-store, and operation qualification;
- proof that a blocked request performs no southbound enumeration.

### PostgreSQL and real-socket tests

- two repository instances observe equivalent pages and tokens;
- concurrent commits after captured high-water are deferred;
- restart preserves token usability;
- organization, Space, and collection isolation;
- compaction/retention invalidates old tokens safely;
- real HTTP request and response framing against PostgreSQL and the native BlobStore composition.

## Non-goals and remaining roadmap work

This slice does not close #1326. It does not by itself prove:

- the complete WebDAV profile;
- infinite synchronization traversal;
- source/target provider round-trip and fidelity accounting;
- restored real-HTTP WebDAV equivalence;
- complete two-Server authenticated product E2E;
- Calendar/CalDAV synchronization;
- external adapter qualification;
- production readiness.

Those claims remain blocked until their dedicated executable evidence is green on an exact accepted commit.
