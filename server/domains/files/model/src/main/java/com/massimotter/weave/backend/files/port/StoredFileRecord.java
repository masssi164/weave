package com.massimotter.weave.backend.files.port;

import com.massimotter.weave.backend.files.domain.FilesAuthority.CanonicalFileRecord;
import java.util.Objects;

/** Persistence-side envelope that keeps opaque blob bindings out of canonical Files metadata. */
public record StoredFileRecord(
        CanonicalFileRecord metadata,
        BlobBinding blobBinding) {

    public StoredFileRecord {
        metadata = Objects.requireNonNull(metadata, "metadata must not be null");
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
