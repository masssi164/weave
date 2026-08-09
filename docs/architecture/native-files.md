# Native Files provider

## Boundary

Files is a provider-neutral canonical domain. WebDAV is a permanent northbound Weave Server interface. Provider selection occurs only behind `FilesProviderPort`.

```text
WebDAV
  -> canonical Files application/domain
    -> FilesProviderPort
      -> weave-native (selected default)
      -> s3 (separate provider adapter)
      -> optional external providers
```

`weave-native` owns canonical relational metadata, hierarchy, versions, rights, locks, lifecycle, tombstones, operation intents, change state and reconciliation metadata in Weave PostgreSQL/JPA persistence.

Apache OpenDAL is an internal filesystem blob I/O abstraction of the native provider. OpenDAL types do not cross the provider boundary.

S3 is not a native blob backend. It is an independent Files provider behind `FilesProviderPort` and may use its own adapter-specific dependencies and credentials without becoming part of `weave-native`.

## Blob authority

The native provider stores private immutable blob content in filesystem storage through OpenDAL. Blob data operations use the OpenDAL operator boundary. Raw NIO blob data paths are not parallel native authorities.

Canonical/member paths are not blob keys. Blob references are opaque, scoped and validated. Immutable publication verifies content digest and length.

## Atomicity model

PostgreSQL and native filesystem blob storage do not share an ACID transaction. Writes therefore use a durable operation-intent/reconciliation boundary:

1. persist a pending canonical operation intent;
2. stream and verify the immutable blob through OpenDAL;
3. atomically commit canonical version metadata, change revision and outbox state in PostgreSQL;
4. mark the intent complete;
5. bounded reconciliation handles interrupted states.

A failed or ambiguous blob/database boundary never becomes silent success.

## Filesystem behavior

Publication uses a private temporary key and same-backend atomic rename when the required OpenDAL capability is present. Capability checks are part of native configuration validation. A filesystem configuration that cannot satisfy required immutable-publication semantics fails closed.

## Separate S3 provider

The S3 provider is a southbound provider implementation selected independently behind `FilesProviderPort`. Its SDK, credentials, health checks and provider-specific semantics are scoped to that adapter. S3-provider tests and operations evidence are not native Files evidence.

## Security

Native filesystem storage enforces root containment and rejects symlink substitution. Raw blob keys are excluded from member responses, logs and support evidence. Reconciliation is bounded and tenant/context scoped.

## Fresh-start policy

No Nextcloud/WebDAV/S3 content import, dual write, compatibility reader or background adoption job is part of the native-provider cutover. Optional providers remain replaceable behind the same canonical port.
