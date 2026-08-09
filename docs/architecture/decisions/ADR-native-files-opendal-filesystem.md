# ADR: OpenDAL as the native Files storage infrastructure adapter

Status: Accepted for the native-provider closure track; readiness remains gated by PR #1325 evidence.

## Context

`FilesProviderPort` is the provider-selection boundary. `weave-native` is one Provider Adapter. S3 is a different Provider Adapter and must not become a storage mode of `weave-native`.

Provider selection and technology access are different extension points. The project-wide terminology is defined in [`../provider-and-infrastructure-boundaries.md`](../provider-and-infrastructure-boundaries.md):

- Provider Port / Provider Adapter select the implementation of a canonical domain capability.
- Infrastructure Port / Infrastructure Adapter select how that implementation accesses a storage engine, standards library or protocol technology.

The native provider needs private immutable blob storage while Weave owns canonical metadata, authorization, lifecycle, versions, locks and reconciliation in PostgreSQL/JPA.

## Decision

`weave-native` uses `BlobStorePort` as its storage Infrastructure Port and an Apache OpenDAL-backed filesystem implementation as the corresponding Infrastructure Adapter.

- The native implementation uses the OpenDAL filesystem service.
- OpenDAL is not a Files provider and does not own canonical Files semantics.
- OpenDAL types do not leak into `FilesProviderPort`, canonical Files values or member-facing APIs.
- S3 remains independently selectable behind `FilesProviderPort`.
- A separate S3 Provider Adapter may itself use OpenDAL's S3 service behind its own Infrastructure Port if its capability and operational requirements are satisfied.
- Reusing OpenDAL below two Provider Adapters does not collapse those providers into one; the provider boundary is defined above the infrastructure layer.
- Blob references are opaque and scoped; canonical member paths are not blob keys.
- PostgreSQL remains canonical for metadata and lifecycle. The native filesystem stores blob bytes only.
- Publication must be immutable and fail closed when required filesystem/OpenDAL capabilities are unavailable.
- Raw NIO may be used only for filesystem sandbox validation, permissions and durability operations that OpenDAL does not expose; it is not a second blob data path.

## Access-control boundary

The infrastructure layer is deliberately not called an "access control layer". Authentication, authorization, rights and policy remain canonical Weave concerns and execute before storage access. OpenDAL is a technical storage-access abstraction, not an authorization authority.

## Transitional marker

The current application-facing Files API still carries byte-array content objects. This is **not** accepted as proof of bounded streaming for large payloads. PR #1325 may not close the Files acceptance criterion until the northbound/native write and read path has a bounded streaming contract and corresponding tests. This marker must be removed, not merely reworded, when that implementation lands.

## Consequences

The native Files provider has no S3 backend switch and no native-S3 tests. S3-provider qualification is separate and cannot be cited as native Files evidence.

OpenDAL may nevertheless be reused as an infrastructure library by the independent S3 provider. Such reuse must remain below the S3 provider boundary and must not introduce S3 configuration or semantics into `weave-native`.
