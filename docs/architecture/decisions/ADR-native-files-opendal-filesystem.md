# ADR: OpenDAL as the native Files storage infrastructure adapter

Status: Accepted for the incremental native-provider closure track under issue #1326.

## Context

`FilesProviderPort` is the provider-selection boundary. `weave-native` is the canonical Files
provider adapter. Filesystem and future S3-compatible object storage are infrastructure adapters
below its `BlobStorePort`; neither is a second Files domain or provider.

Provider selection and technology access are different extension points. The project-wide terminology is defined in [`../provider-and-infrastructure-boundaries.md`](../provider-and-infrastructure-boundaries.md):

- Provider Port / Provider Adapter select the implementation of a canonical domain capability.
- Infrastructure Port / Infrastructure Adapter select how that implementation accesses a storage engine, standards library or protocol technology.

The native provider needs private immutable blob storage while Weave owns canonical metadata, authorization, lifecycle, versions, locks and reconciliation in PostgreSQL/JPA.

## Decision

`weave-native` uses `BlobStorePort` as its storage Infrastructure Port and an Apache OpenDAL-backed filesystem implementation as the corresponding Infrastructure Adapter.

- The native implementation uses the OpenDAL filesystem service.
- OpenDAL is not a Files provider and does not own canonical Files semantics.
- OpenDAL types do not leak into `FilesProviderPort`, canonical Files values or member-facing APIs.
- A future S3-compatible `BlobStorePort` adapter may use OpenDAL's S3 service if its capability,
  immutability, durability and operational requirements are qualified.
- Changing the infrastructure adapter does not change provider identity, canonical semantics,
  member paths, authorization, mutation plans or northbound WebDAV contracts.
- Blob references are opaque and scoped; canonical member paths are not blob keys.
- PostgreSQL remains canonical for metadata and lifecycle. The native filesystem stores blob bytes only.
- Publication must be immutable and fail closed when required filesystem/OpenDAL capabilities are unavailable.
- Raw NIO may be used only for filesystem sandbox validation, permissions and durability operations that OpenDAL does not expose; it is not a second blob data path.

## Access-control boundary

The infrastructure layer is deliberately not called an "access control layer". Authentication, authorization, rights and policy remain canonical Weave concerns and execute before storage access. OpenDAL is a technical storage-access abstraction, not an authorization authority.

## Transitional marker

Native WebDAV PUT, GET and HEAD now have the bounded streaming contract and incremental executable
evidence linked from the canonical native Files architecture page. The canonical Files domain,
selected provider port, native adapter and BlobStore port expose no whole-representation byte-array
fallback. Historical external adapters remain explicitly unqualified until they implement the
same bounded profile. The complete
authenticated PostgreSQL/blob real-HTTP, two-instance and restart topology remains required before
issue #1326 can close.

## Consequences

The initial qualified infrastructure adapter remains OpenDAL filesystem. S3-compatible
`BlobStorePort` qualification is separate infrastructure work and cannot be inferred from
filesystem evidence.
