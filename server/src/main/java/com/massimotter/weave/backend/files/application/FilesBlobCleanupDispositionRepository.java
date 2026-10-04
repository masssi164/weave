package com.massimotter.weave.backend.files.application;

import com.massimotter.weave.backend.files.port.BlobStorePort.BlobReference;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobScope;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Adapter-private persistence boundary for terminal native Files blob cleanup.
 *
 * <p>Exact bindings cross this boundary only inside the server process. They must never enter a
 * member response, audit payload, support bundle, portability export, or Files change entry.</p>
 */
public interface FilesBlobCleanupDispositionRepository {

    String VERSION = "weave.files-blob-cleanup-disposition/v1";

    CleanupWork lockWork(String operationRef);

    List<RecordedDisposition> recorded(String operationRef);

    ReferenceStatus recheck(CleanupWork work, BlobReference binding);

    void record(
            CleanupWork work,
            BlobReference binding,
            String bindingDigest,
            Disposition disposition,
            Instant recordedAt);

    enum Disposition {
        STILL_REFERENCED,
        STILL_PROTECTED,
        DELETED,
        ALREADY_ABSENT
    }

    enum ReferenceStatus {
        STILL_REFERENCED,
        STILL_PROTECTED,
        DELETE_ALLOWED
    }

    record CleanupWork(
            String operationRef,
            BlobScope scope,
            List<BlobReference> plannedBindings) {
        public CleanupWork {
            operationRef = required(operationRef, "operationRef");
            scope = Objects.requireNonNull(scope, "scope must not be null");
            plannedBindings = plannedBindings == null ? List.of() : List.copyOf(plannedBindings);
        }
    }

    record RecordedDisposition(
            String operationRef,
            String dispositionVersion,
            String bindingDigest,
            BlobReference binding,
            Disposition disposition,
            Instant recordedAt) {
        public RecordedDisposition {
            operationRef = required(operationRef, "operationRef");
            if (!VERSION.equals(dispositionVersion)) {
                throw new IllegalArgumentException("unsupported Files blob cleanup disposition version");
            }
            bindingDigest = digest(bindingDigest);
            binding = Objects.requireNonNull(binding, "binding must not be null");
            disposition = Objects.requireNonNull(disposition, "disposition must not be null");
            recordedAt = Objects.requireNonNull(recordedAt, "recordedAt must not be null");
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String digest(String value) {
        String required = required(value, "bindingDigest");
        if (!required.matches("sha256:[a-f0-9]{64}")) {
            throw new IllegalArgumentException("bindingDigest must be a sha256 digest");
        }
        return required;
    }
}
