# Native Files provider

## Boundary

Files is a provider-neutral canonical domain. WebDAV is a permanent northbound Weave Server interface. Provider selection occurs only behind `FilesProviderPort`.

```text
WebDAV
  -> canonical Files application/domain
    -> FilesProviderPort
      -> weave-native (selected default)
      -> optional external providers
```

`weave-native` owns canonical relational metadata, hierarchy, versions, rights, locks, lifecycle, tombstones, operation intents, change state and reconciliation metadata in Weave PostgreSQL/JPA persistence.

Apache OpenDAL is an internal blob I/O abstraction of the native provider. OpenDAL types and backend credentials do not cross the provider boundary.

## Blob authority

The native provider supports private filesystem and S3-compatible blob storage through OpenDAL. Blob data operations use the OpenDAL operator boundary. Direct AWS SDK or raw NIO blob data paths are not parallel authorities.

Canonical/member paths are not blob keys. Blob references are opaque, scoped and validated. Immutable publication verifies content digest and length.

## Atomicity model

PostgreSQL and blob storage do not share an ACID transaction. Writes therefore use a durable operation-intent/reconciliation boundary:

1. persist a pending canonical operation intent;
2. stream and verify the immutable blob through OpenDAL;
3. atomically commit canonical version metadata, change revision and outbox state in PostgreSQL;
4. mark the intent complete;
5. bounded reconciliation handles interrupted states.

A failed or ambiguous blob/database boundary never becomes silent success.

## Backend behavior

Filesystem publication uses a private temporary key and same-backend rename when the required capability is present. S3-compatible publication uses conditional create semantics and verifies an already-present object after a race.

OpenDAL capability checks are part of configuration validation. A backend that cannot satisfy required immutable-publication semantics fails closed.

## Security

Filesystem storage enforces root containment and rejects symlink substitution. Provider credentials and raw blob keys are excluded from member responses, logs and support evidence. Reconciliation is bounded and tenant/context scoped.

## Fresh-start policy

No Nextcloud/WebDAV content import, dual write, compatibility reader or background adoption job is part of the native-provider cutover. Optional providers remain replaceable behind the same canonical port.