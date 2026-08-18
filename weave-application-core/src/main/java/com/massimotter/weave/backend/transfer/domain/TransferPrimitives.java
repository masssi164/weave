package com.massimotter.weave.backend.transfer.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Shared provider-neutral transfer vocabulary. Domain payloads remain typed by their owning domain. */
public final class TransferPrimitives {
    private TransferPrimitives() {
    }

    public enum Domain {
        FILES,
        CALENDAR,
        CHAT
    }

    public enum Lifecycle {
        ACTIVE,
        TOMBSTONED,
        ARCHIVED
    }

    public enum Provenance {
        NATIVE,
        IMPORTED,
        OBSERVED,
        TRANSFORMED,
        RESTORED
    }

    public enum LossClass {
        PORTABLE,
        LOSSY,
        UNSUPPORTED,
        MANUAL_REVIEW,
        VENDOR_LOCKED,
        ARCHIVE_ONLY
    }

    public record TransferFormatVersion(int value) {
        public TransferFormatVersion {
            if (value < 1) {
                throw new IllegalArgumentException("transfer format version must be positive");
            }
        }
    }

    public record TransferCheckpoint(String cursor, long sequence) {
        public TransferCheckpoint {
            cursor = required(cursor, "checkpoint cursor");
            if (sequence < 0) {
                throw new IllegalArgumentException("checkpoint sequence must not be negative");
            }
        }
    }

    public record ObjectMetadata(
            CanonicalObjectId id,
            Domain domain,
            String objectKind,
            long revision,
            Lifecycle lifecycle,
            Provenance provenance,
            Instant observedAt,
            String payloadDigest,
            List<CanonicalObjectId> dependencies) {
        public ObjectMetadata {
            id = Objects.requireNonNull(id, "id must not be null");
            domain = Objects.requireNonNull(domain, "domain must not be null");
            objectKind = required(objectKind, "object kind");
            if (revision < 1) {
                throw new IllegalArgumentException("revision must be positive");
            }
            lifecycle = Objects.requireNonNull(lifecycle, "lifecycle must not be null");
            provenance = Objects.requireNonNull(provenance, "provenance must not be null");
            observedAt = Objects.requireNonNull(observedAt, "observedAt must not be null");
            payloadDigest = required(payloadDigest, "payload digest");
            dependencies = List.copyOf(dependencies == null ? List.of() : dependencies);
        }
    }

    public record LossRecord(
            CanonicalObjectId objectId,
            String field,
            LossClass classification,
            String reason) {
        public LossRecord {
            objectId = Objects.requireNonNull(objectId, "objectId must not be null");
            field = required(field, "loss field");
            classification = Objects.requireNonNull(classification, "classification must not be null");
            reason = required(reason, "loss reason");
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
