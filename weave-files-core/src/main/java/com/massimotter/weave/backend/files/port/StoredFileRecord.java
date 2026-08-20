package com.massimotter.weave.backend.files.port;

import com.massimotter.weave.backend.files.domain.FilesAuthority.CanonicalFileRecord;
import java.util.Objects;

/** Persistence-side envelope that keeps opaque blob bindings out of canonical Files metadata. */
public record StoredFileRecord(
        CanonicalFileRecord metadata,
        BlobBinding blobBinding,
        long adapterRowVersion) {

    public StoredFileRecord(CanonicalFileRecord metadata, BlobBinding blobBinding) {
        this(metadata, blobBinding, 0);
    }

    public StoredFileRecord {
        metadata = Objects.requireNonNull(metadata, "metadata must not be null");
        if (adapterRowVersion < 0) {
            throw new IllegalArgumentException("adapterRowVersion must not be negative");
        }
    }

    /** Adapter row versions are concurrency observations, not canonical record identity. */
    @Override
    public boolean equals(Object candidate) {
        return this == candidate
                || candidate instanceof StoredFileRecord other
                && Objects.equals(metadata, other.metadata)
                && Objects.equals(blobBinding, other.blobBinding);
    }

    @Override
    public int hashCode() {
        return Objects.hash(metadata, blobBinding);
    }

    /** Persistence-private opaque reference; callers must validate it before blob access. */
    public record BlobBinding(String opaqueReference) {
        public BlobBinding {
            if (opaqueReference == null || opaqueReference.isBlank()) {
                throw new IllegalArgumentException("opaqueReference must not be blank");
            }
            opaqueReference = opaqueReference.trim();
        }
    }
}
