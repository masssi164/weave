package com.massimotter.weave.backend.data.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Provider- and persistence-independent primitives shared by the canonical Files,
 * Calendar and Chat transfer paths.
 */
public final class CanonicalData {

    private CanonicalData() {
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

    public enum ProvenanceKind {
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

    public enum TransferStage {
        IMPORT,
        EXPORT
    }

    public record ObjectId(Domain domain, String value) {
        public ObjectId {
            domain = Objects.requireNonNull(domain, "domain must not be null");
            value = requireText(value, "canonical object id");
        }
    }

    public record Scope(String organizationRef, String contextRef) {
        public Scope {
            organizationRef = requireText(organizationRef, "organizationRef");
            contextRef = optionalText(contextRef);
        }
    }

    public record ModelVersion(String value) {
        public ModelVersion {
            value = requireText(value, "canonical model version");
        }
    }

    public record TransferFormatVersion(int value) {
        public TransferFormatVersion {
            if (value < 1) {
                throw new IllegalArgumentException("transfer format version must be positive");
            }
        }
    }

    public record Revision(long value) {
        public Revision {
            if (value < 1) {
                throw new IllegalArgumentException("revision must be positive");
            }
        }
    }

    public record Provenance(ProvenanceKind kind, String sourceRef, Instant observedAt) {
        public Provenance {
            kind = Objects.requireNonNull(kind, "provenance kind must not be null");
            sourceRef = optionalText(sourceRef);
            observedAt = Objects.requireNonNull(observedAt, "observedAt must not be null");
            if (kind != ProvenanceKind.NATIVE && sourceRef == null) {
                throw new IllegalArgumentException("non-native provenance requires sourceRef");
            }
        }
    }

    public record ProviderBindingRef(String adapterKey, long revision) {
        public ProviderBindingRef {
            adapterKey = requireText(adapterKey, "adapterKey");
            if (revision < 1) {
                throw new IllegalArgumentException("provider binding revision must be positive");
            }
        }
    }

    /**
     * Private southbound mapping. Provider identifiers remain connector/persistence
     * metadata and must never become a northbound object identity.
     */
    public record ProviderObjectMapping(
            ObjectId objectId,
            ProviderBindingRef binding,
            String providerObjectRef,
            String sourceVersion,
            Instant observedAt) {
        public ProviderObjectMapping {
            objectId = Objects.requireNonNull(objectId, "objectId must not be null");
            binding = Objects.requireNonNull(binding, "binding must not be null");
            providerObjectRef = requireText(providerObjectRef, "providerObjectRef");
            sourceVersion = optionalText(sourceVersion);
            observedAt = Objects.requireNonNull(observedAt, "observedAt must not be null");
        }
    }

    public record Dependency(ObjectId source, ObjectId target, String relation) {
        public Dependency {
            source = Objects.requireNonNull(source, "source must not be null");
            target = Objects.requireNonNull(target, "target must not be null");
            relation = requireText(relation, "relation");
            if (source.equals(target)) {
                throw new IllegalArgumentException("dependency must not reference itself");
            }
        }
    }

    public record LossObservation(
            ObjectId objectId,
            String fieldPath,
            LossClass classification,
            String reason) {
        public LossObservation {
            objectId = Objects.requireNonNull(objectId, "objectId must not be null");
            fieldPath = requireText(fieldPath, "fieldPath");
            classification = Objects.requireNonNull(classification, "classification must not be null");
            reason = requireText(reason, "reason");
        }
    }

    public record TransferRunId(String value) {
        public TransferRunId {
            value = requireText(value, "transfer run id");
        }
    }

    public record CheckpointKey(TransferRunId runId, TransferStage stage) {
        public CheckpointKey {
            runId = Objects.requireNonNull(runId, "runId must not be null");
            stage = Objects.requireNonNull(stage, "stage must not be null");
        }
    }

    public record Checkpoint(long sequence, String cursor, boolean complete) {
        public Checkpoint {
            if (sequence < 0) {
                throw new IllegalArgumentException("checkpoint sequence must not be negative");
            }
            cursor = optionalText(cursor);
            if (!complete && sequence > 0 && cursor == null) {
                throw new IllegalArgumentException("an incomplete advanced checkpoint requires a cursor");
            }
        }

        public static Checkpoint initial() {
            return new Checkpoint(0, null, false);
        }
    }

    public record IdempotencyKey(String value) {
        public IdempotencyKey {
            value = requireText(value, "idempotency key");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
