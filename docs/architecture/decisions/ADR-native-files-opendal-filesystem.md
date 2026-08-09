# ADR: OpenDAL filesystem storage for weave-native Files

Status: Accepted for the native-provider closure track; readiness remains gated by PR #1325 evidence.

## Context

`FilesProviderPort` is the provider boundary. `weave-native` is one provider implementation. S3 is a different provider adapter and must not become a storage mode of `weave-native`.

The native provider needs private immutable blob storage while Weave owns canonical metadata, authorization, lifecycle, versions, locks and reconciliation in PostgreSQL/JPA.

## Decision

`weave-native` uses Apache OpenDAL only as its private filesystem blob I/O abstraction.

- The native implementation uses the OpenDAL filesystem service.
- S3 credentials, endpoints, SDK types and S3-specific behavior are forbidden from the native blob implementation.
- S3 remains independently selectable behind `FilesProviderPort`.
- Blob references are opaque and scoped; canonical member paths are not blob keys.
- PostgreSQL remains canonical for metadata and lifecycle. The filesystem stores blob bytes only.
- Publication must be immutable and fail closed when required filesystem/OpenDAL capabilities are unavailable.
- Raw NIO may be used only for filesystem sandbox validation, permissions and durability operations that OpenDAL does not expose; it is not a second blob data path.

## Transitional marker

The current application-facing Files API still carries byte-array content objects. This is **not** accepted as proof of bounded streaming for large payloads. PR #1325 may not close the Files acceptance criterion until the northbound/native write and read path has a bounded streaming contract and corresponding tests. This marker must be removed, not merely reworded, when that implementation lands.

## Consequences

The native Files provider has no S3 backend switch and no native-S3 tests. S3-provider qualification is separate and cannot be cited as native Files evidence.
