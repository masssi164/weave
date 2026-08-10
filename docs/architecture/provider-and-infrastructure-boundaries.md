# Provider and infrastructure boundaries

## Purpose

Weave separates provider portability from technology access. These are different extension points and must not be collapsed into one abstraction.

The canonical layering is:

```text
Northbound standard/API facade
    -> canonical application/domain
        -> Provider Port
            -> Provider Adapter
                -> Infrastructure Port
                    -> Infrastructure Adapter
                        -> library / protocol / storage technology
```

The terms in this document are normative for architecture documentation and new code.

## Terminology

### Northbound facade

A stable Weave-owned member/client contract such as WebDAV, CalDAV/iCalendar or Matrix Client-Server. Northbound standards are server surfaces and are not provider feature flags.

### Canonical domain

The provider-neutral Weave model and application behavior. Canonical IDs, authorization, lifecycle, synchronization semantics and application contracts belong here.

### Provider Port

The southbound business-level substitution boundary for one canonical domain. Examples:

- `FilesProviderPort`
- `CalendarProviderPort`
- `ChatProviderPort`

Changing a provider must not change the northbound contract or canonical semantics.

### Provider Adapter

A concrete implementation of a Provider Port. Examples:

- `WeaveNativeFilesAdapter`
- `WeaveNativeCalendarAdapter`
- `WeaveNativeChatAdapter`
- S3 Files provider
- Nextcloud/WebDAV Files provider
- CalDAV/Nextcloud Calendar provider
- Synapse/Matrix-backed Chat provider

A provider adapter answers **which implementation supplies the canonical domain capability**.

### Infrastructure Port

A narrow technology-facing port used inside a provider adapter or protocol facade. It is not a provider selection boundary and must not own canonical business semantics.

Examples:

- `BlobStorePort` for immutable private blob I/O in the native Files provider
- `ObjectStoragePort` for object-storage access used by the separate S3 Files provider
- `IcalendarCodec` and `RecurrenceEngine` for iCalendar/RFC 5545 semantics
- `MatrixProtocolCodec` for Matrix wire projection
- repository ports for canonical persistence

### Infrastructure Adapter

A concrete implementation of an Infrastructure Port. It answers **how a provider or facade talks to a technology**.

Examples:

- `FilesystemBlobStore`, the OpenDAL filesystem adapter behind `BlobStorePort`
- `OpenDalS3ObjectStorageAdapter` behind `ObjectStoragePort` for the separate S3 Files provider
- iCal4j adapters behind `IcalendarCodec` / `RecurrenceEngine`
- Ruma + jni-rs adapter behind `MatrixProtocolCodec`
- Spring Data JPA/Hibernate adapters behind repository ports

### Technology/library

A third-party library, protocol implementation or storage system hidden behind an Infrastructure Adapter. Examples include Apache OpenDAL, iCal4j, Ruma, jni-rs, Hibernate and PostgreSQL.

## Access control is a different concern

Do not call the infrastructure layer an "access control layer". Access control means authentication/authorization, rights and policy enforcement. Those decisions remain in canonical Weave application/domain/security boundaries and must not be delegated to OpenDAL, iCal4j, Ruma or a provider SDK.

For explanatory prose, "technical access layer" may be used, but code and architecture documents should prefer the precise terms **Infrastructure Port** and **Infrastructure Adapter**.

## Files example

```text
WebDAV
  -> canonical Files
    -> FilesProviderPort
      -> WeaveNativeFilesAdapter
        -> BlobStorePort
          -> FilesystemBlobStore
            -> Apache OpenDAL (filesystem service)
              -> private filesystem storage

      -> WeaveS3FilesAdapter
        -> ObjectStoragePort
          -> OpenDalS3ObjectStorageAdapter
            -> Apache OpenDAL (S3 service)
              -> S3
```

OpenDAL is therefore not a Files provider. The same OpenDAL library is deliberately reused by multiple provider adapters because it lives below the provider boundary.

For `weave-native`, the selected storage service remains the private filesystem service. S3 remains a separate Files provider even though its current infrastructure implementation also uses OpenDAL internally.

Provider-specific configuration remains isolated: filesystem root/capability configuration belongs to `weave-native`; endpoint, bucket and S3 credentials belong only to the S3 Provider Adapter and its infrastructure adapter.

## Calendar example

```text
CalDAV/iCalendar
  -> canonical Calendar
    -> CalendarProviderPort
      -> weave-native Calendar provider
        -> IcalendarCodec / RecurrenceEngine
          -> iCal4j infrastructure adapter
            -> iCal4j
```

iCal4j owns standards syntax and recurrence mechanics; Weave owns canonical event state, authorization, lifecycle, persistence and sync semantics.

## Chat example

```text
Matrix Client-Server
  -> MatrixProtocolCodec
    -> Ruma/JNI infrastructure adapter
      -> Ruma + jni-rs
        -> canonical Chat application/domain
          -> ChatProviderPort
            -> weave-native Chat provider
            -> optional external Chat providers
```

The Matrix northbound facade is permanent. Ruma is a protocol infrastructure library, not a Chat provider. Provider selection remains behind `ChatProviderPort`.

## Dependency direction

Canonical domain/application modules must not depend on OpenDAL, iCal4j, Ruma, jni-rs, provider SDKs or storage-specific types. Concrete infrastructure adapters depend inward on the narrow ports they implement.

Provider adapters may compose infrastructure ports, but infrastructure adapters must not choose providers or redefine canonical authorization, IDs, lifecycle or synchronization behavior.

## Review rule

When introducing a dependency or adapter, reviewers should ask two separate questions:

1. **Provider question:** does this change which implementation supplies the canonical domain capability? If yes, it belongs behind the Provider Port.
2. **Technology question:** does this only change how an existing provider/facade talks to storage, a protocol grammar or another technical system? If yes, it belongs behind an Infrastructure Port.

This distinction is required for Files, Calendar and Chat and should be reused for future canonical domains.
