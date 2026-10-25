package com.massimotter.weave.backend.files.domain;

import com.massimotter.weave.backend.files.domain.FilesDomain.FileObject;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileVersion;
import java.time.Instant;

public final class FilesAuthority {

    private FilesAuthority() {
    }

    public record CanonicalFileRecord(
            String organizationRef,
            String spaceRef,
            FileObject object,
            FileVersion version,
            String contentDigest,
            long providerBindingRevision,
            Lifecycle lifecycle,
            Instant observedAt) {

        public CanonicalFileRecord {
            organizationRef = required(organizationRef, "organizationRef");
            spaceRef = required(spaceRef, "spaceRef");
            object = java.util.Objects.requireNonNull(object, "object must not be null");
            version = version == null ? FileVersion.unknown() : version;
            if (contentDigest != null && !contentDigest.matches("sha256:[a-f0-9]{64}")) {
                throw new IllegalArgumentException("contentDigest must be a sha256 digest");
            }
            if (providerBindingRevision < 1) {
                throw new IllegalArgumentException("providerBindingRevision must be positive");
            }
            lifecycle = java.util.Objects.requireNonNull(lifecycle, "lifecycle must not be null");
            observedAt = java.util.Objects.requireNonNull(observedAt, "observedAt must not be null");
        }
    }

    public enum Lifecycle { ACTIVE, TOMBSTONED }

    /** Only a digest of the client-visible lock token is durable. */
    public record FileLockRecord(
            String organizationRef,
            String spaceRef,
            FilePath path,
            String tokenDigest,
            String ownerRef,
            long fence,
            Instant expiresAt,
            Instant createdAt) {

        public FileLockRecord {
            organizationRef = required(organizationRef, "organizationRef");
            spaceRef = required(spaceRef, "spaceRef");
            path = java.util.Objects.requireNonNull(path, "path must not be null");
            if (tokenDigest == null || !tokenDigest.matches("sha256:[a-f0-9]{64}")) {
                throw new IllegalArgumentException("tokenDigest must be a sha256 digest");
            }
            ownerRef = required(ownerRef, "ownerRef");
            if (fence < 1) {
                throw new IllegalArgumentException("lock fence must be positive");
            }
            expiresAt = java.util.Objects.requireNonNull(expiresAt, "expiresAt must not be null");
            createdAt = java.util.Objects.requireNonNull(createdAt, "createdAt must not be null");
            if (!expiresAt.isAfter(createdAt)) {
                throw new IllegalArgumentException("lock expiry must follow creation");
            }
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

}
