# Native Files provider

## Boundary

Files is a provider-neutral canonical domain. WebDAV is a permanent northbound Weave Server interface. Provider selection occurs only behind `FilesProviderPort`.

```text
WebDAV
  -> canonical Files application/domain
    -> FilesProviderPort                    (Provider Port)
      -> weave-native                       (Provider Adapter, selected default)
        -> BlobStorePort                    (Infrastructure Port)
          -> OpenDAL filesystem adapter     (Infrastructure Adapter)
            -> Apache OpenDAL
              -> private filesystem storage

      -> s3                                 (separate Provider Adapter)
        -> object-storage infrastructure port
          -> OpenDAL S3 adapter where appropriate
            -> Apache OpenDAL
              -> S3

      -> optional external providers
```

The general terminology is defined in [`provider-and-infrastructure-boundaries.md`](provider-and-infrastructure-boundaries.md).

`weave-native` owns canonical relational metadata, hierarchy, versions, rights, locks, lifecycle, tombstones, operation intents, change state and reconciliation metadata in Weave PostgreSQL/JPA persistence.

Apache OpenDAL is **not a Files provider**. It is an infrastructure-level storage abstraction that may be used by Files provider adapters where appropriate. OpenDAL types never cross the provider or canonical-domain boundary.

For the `weave-native` provider, OpenDAL uses the filesystem service for private immutable blob storage. S3 remains an independently selectable Files provider even if the S3 provider also chooses OpenDAL internally for object-storage access.

## Blob authority

The native provider stores private immutable blob content in filesystem storage through the `BlobStorePort` Infrastructure Port. The concrete infrastructure adapter uses OpenDAL's filesystem service. Raw NIO blob data paths are not parallel native authorities.

Canonical/member paths are not blob keys. Blob references are opaque, scoped and validated. Immutable publication verifies content digest and length.

## Atomicity model

PostgreSQL and native filesystem blob storage do not share an ACID transaction. Writes therefore use a durable operation-intent/reconciliation boundary:

1. persist a pending canonical operation intent;
2. stream and verify the immutable blob through the Files storage Infrastructure Port;
3. atomically commit canonical version metadata, change revision and outbox state in PostgreSQL;
4. mark the intent complete;
5. bounded reconciliation handles interrupted states.

A failed or ambiguous blob/database boundary never becomes silent success.

## Filesystem behavior

The `weave-native` OpenDAL infrastructure adapter uses the filesystem service. Publication uses a private temporary key and same-backend atomic rename when the required capability is present. Capability checks are part of native configuration validation. A filesystem configuration that cannot satisfy required immutable-publication semantics fails closed.

## Separate S3 provider

The S3 provider is a southbound **Provider Adapter** selected independently behind `FilesProviderPort`. S3 is not a storage mode of `weave-native`.

Its object-storage implementation may use OpenDAL's S3 service behind its own Infrastructure Port if that satisfies the provider's capability, concurrency and operational requirements. Reusing OpenDAL does not merge the S3 provider into `weave-native`; provider identity is defined by the Provider Port/Adapter boundary, not by the library used below it.

S3-provider tests and operations evidence remain provider-scoped and are not native Files evidence.

## Security

Access control is not delegated to OpenDAL. Canonical authentication, authorization, rights and policy checks occur before the Infrastructure Port is entered.

Native filesystem storage additionally enforces root containment and rejects symlink substitution. Raw blob keys are excluded from member responses, logs and support evidence. Reconciliation is bounded and tenant/context scoped.

## Fresh-start policy

No Nextcloud/WebDAV/S3 content import, dual write, compatibility reader or background adoption job is part of the native-provider cutover. Optional providers remain replaceable behind the same canonical port.
