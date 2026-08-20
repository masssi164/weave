package com.massimotter.weave.backend.files.port;

import java.time.Instant;
import java.util.Objects;

/** Fresh, operation-specific qualification for bounded canonical WebDAV basicsearch. */
public record FilesWebDavSearchQualification(
        String status,
        boolean verified,
        Instant observedAt,
        Instant expiresAt,
        String evidenceRef) {

    public static final String EVIDENCE_REF =
            "weave:docs/evidence/native-files-webdav-basicsearch.md";

    public FilesWebDavSearchQualification {
        status = required(status, "status");
        observedAt = Objects.requireNonNull(observedAt, "observedAt must not be null");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (!expiresAt.isAfter(observedAt)) {
            throw new IllegalArgumentException("expiresAt must be after observedAt");
        }
        if (verified) {
            evidenceRef = required(evidenceRef, "evidenceRef");
        }
    }

    public static FilesWebDavSearchQualification nativeVerified(Instant observedAt) {
        Instant observed = Objects.requireNonNull(observedAt, "observedAt must not be null");
        return new FilesWebDavSearchQualification(
                "native",
                true,
                observed,
                observed.plusSeconds(60),
                EVIDENCE_REF);
    }

    public static FilesWebDavSearchQualification blocked(Instant observedAt) {
        Instant observed = Objects.requireNonNull(observedAt, "observedAt must not be null");
        return new FilesWebDavSearchQualification(
                "blocked",
                false,
                observed,
                observed.plusSeconds(60),
                null);
    }

    public boolean qualifiedAt(Instant now) {
        Instant observedNow = Objects.requireNonNull(now, "now must not be null");
        return verified
                && ("native".equals(status) || "emulated".equals(status))
                && !observedAt.isAfter(observedNow)
                && expiresAt.isAfter(observedNow)
                && evidenceRef != null
                && !evidenceRef.isBlank();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
