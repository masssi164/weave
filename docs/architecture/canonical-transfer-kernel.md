# Canonical transfer kernel

Status: active foundation for issues #1012 and #1299.

## Purpose

Weave owns provider-independent collaboration data for Files, Calendar, and Chat. A provider can be a source, a target, or a synchronized projection, but provider schemas and identifiers never define the Weave product contract.

The transfer kernel supplies the shared mechanics required to move typed domain objects without creating one untyped universal domain model.

## Version coordinates

Four versions remain independent:

- Flyway schema version: physical PostgreSQL structure.
- Canonical model version: typed Files, Calendar, and Chat meaning.
- Transfer format version: provider-independent import/export envelope.
- Adapter profile version: one provider connector's mapping and capability contract.

A database backup and a serialization of JPA entities are not canonical transfer formats.

## Shared model

The first executable kernel contains:

- stable `CanonicalObjectId` values;
- domain, object kind, revision, lifecycle, provenance, observation time, payload digest, and dependencies;
- a versioned `CanonicalTransferEnvelope` containing typed domain items;
- resumable `TransferRun` state and checkpoints;
- deterministic aggregate digests and idempotency keys;
- provider source and target ports;
- one explicit portability outcome per object field;
- the six outcomes `portable`, `lossy`, `unsupported`, `manual_review`, `vendor_locked`, and `archive_only`.

The kernel never contains JPA annotations, provider DTOs, provider URLs, credentials, WebDAV, CalDAV, Matrix, MCP, OpenDAL, or iCal4j types.

## Connector flow

The executable direction is:

```text
provider source
  -> typed canonical batch
    -> canonical envelope and digest
      -> target preflight
        -> idempotent target apply
          -> target readback verification
            -> durable checkpoint advance
```

If a target mutation succeeds but acknowledgement persistence fails, retry uses the same deterministic idempotency key. A conforming target returns the original receipt rather than duplicating objects.

Provider-specific receipts remain opaque southbound data. Northbound WebDAV, CalDAV, Matrix, and Files/Calendar MCP surfaces continue to expose canonical identities only.

## Loss accounting

Every known non-equivalent field receives exactly one non-conflicting classification. Conflicting classifications for the same canonical object and field fail before target mutation. Loss records referring to objects outside the current batch also fail.

The promise is no unaccounted data loss, not universal lossless conversion.

## Current proof

`./gradlew canonicalDataCi` runs deterministic mixed Files/Calendar/Chat transfer tests covering:

- two bounded source batches;
- all six portability classes;
- target mutation followed by an injected acknowledgement failure;
- retry with the same idempotency key and no duplicate target objects;
- rejection of conflicting loss classifications before target mutation.

The next persistence step under #1320 implements `TransferRunRepository` with JPA/Flyway and proves restart, concurrent ownership, and backup/restore. Domain issues #1326, #1301, and #1302 then supply their typed transfer items and connector mappings.
