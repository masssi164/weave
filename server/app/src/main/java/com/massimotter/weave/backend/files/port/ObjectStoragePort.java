package com.massimotter.weave.backend.files.port;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Infrastructure Port for object-storage operations used by a Files Provider Adapter.
 *
 * <p>This port deliberately contains no provider-selection or canonical Files semantics. A provider
 * such as the independently selectable S3 adapter may use an OpenDAL implementation behind this
 * boundary without exposing OpenDAL or S3 SDK types to the canonical Files domain.</p>
 */
public interface ObjectStoragePort {

    boolean configured();

    void check();

    Optional<ObjectMetadata> stat(String key);

    List<ObjectEntry> list(String prefix);

    byte[] read(String key);

    void write(String key, byte[] bytes, String contentType);

    void copy(String sourceKey, String targetKey);

    void delete(String key);

    record ObjectMetadata(long size, String contentType, String version, Instant modifiedAt) {
        public ObjectMetadata {
            if (size < 0) {
                throw new IllegalArgumentException("object size must not be negative");
            }
        }
    }

    record ObjectEntry(String key, ObjectMetadata metadata) {
        public ObjectEntry {
            key = required(key, "key");
            metadata = Objects.requireNonNull(metadata, "metadata must not be null");
        }
    }

    enum FailureCode {
        NOT_FOUND,
        CONFLICT,
        FORBIDDEN,
        UNAVAILABLE
    }

    final class ObjectStorageException extends RuntimeException {
        private final FailureCode code;

        public ObjectStorageException(FailureCode code, String message, Throwable cause) {
            super(message, cause);
            this.code = Objects.requireNonNull(code, "code must not be null");
        }

        public FailureCode code() {
            return code;
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
