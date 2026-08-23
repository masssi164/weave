package com.massimotter.weave.backend.files.port;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Fail-closed, operation-specific qualification for RFC 6578 Files synchronization. */
public record FilesWebDavSyncQualification(
        String status,
        Set<Proof> proofs,
        Instant observedAt,
        Instant expiresAt,
        String evidenceRef) {

    public static final String EVIDENCE_REF =
            "weave:docs/evidence/native-files-webdav-sync-collection.md";
    private static final Set<Proof> COMPLETE_PROOF = Set.copyOf(EnumSet.allOf(Proof.class));

    public FilesWebDavSyncQualification {
        status = required(status, "status");
        proofs = immutableProofs(proofs);
        observedAt = Objects.requireNonNull(observedAt, "observedAt must not be null");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (!expiresAt.isAfter(observedAt)) {
            throw new IllegalArgumentException("expiresAt must be after observedAt");
        }
        if (!proofs.isEmpty()) {
            evidenceRef = required(evidenceRef, "evidenceRef");
        }
    }

    /**
     * Creates a current verified record only when the caller supplies every independently owned
     * proof. No zero-argument verified shortcut exists deliberately.
     */
    public static FilesWebDavSyncQualification verifiedNative(
            Instant observedAt,
            Set<Proof> proofs,
            String evidenceRef) {
        Instant observed = Objects.requireNonNull(observedAt, "observedAt must not be null");
        return new FilesWebDavSyncQualification(
                "native",
                proofs,
                observed,
                observed.plusSeconds(60),
                evidenceRef);
    }

    public static FilesWebDavSyncQualification blocked(Instant observedAt) {
        Instant observed = Objects.requireNonNull(observedAt, "observedAt must not be null");
        return new FilesWebDavSyncQualification(
                "blocked",
                Set.of(),
                observed,
                observed.plusSeconds(60),
                null);
    }

    public boolean qualifiedAt(Instant now) {
        Instant observedNow = Objects.requireNonNull(now, "now must not be null");
        return ("native".equals(status) || "emulated".equals(status))
                && proofs.equals(COMPLETE_PROOF)
                && !observedAt.isAfter(observedNow)
                && expiresAt.isAfter(observedNow)
                && evidenceRef != null
                && !evidenceRef.isBlank();
    }

    public enum Proof {
        INITIAL_SYNC,
        INCREMENTAL_SYNC,
        ATOMIC_TRUNCATION,
        COLLECTION_BOUND_TOKEN,
        IF_STATE_TOKEN,
        AUTHENTICATED_REAL_HTTP,
        TWO_INSTANCE_EQUIVALENCE,
        PROCESS_RESTART,
        ISOLATED_RESTORE,
        SUPPORT_SAFE_OUTPUT
    }

    private static Set<Proof> immutableProofs(Set<Proof> proofs) {
        if (proofs == null || proofs.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("proofs must not contain null");
        }
        return proofs.isEmpty()
                ? Set.of()
                : Set.copyOf(EnumSet.copyOf(proofs));
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(field + " must not be blank or padded");
        }
        return value;
    }
}
