package com.massimotter.weave.backend.agentruntime.domain;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Public, support-safe projection of a workload credential held behind a SecretRef. */
public record RuntimeWorkloadCredentialState(
        String credentialRef,
        RuntimeWorkloadBinding.AuthenticationMethod authenticationMethod,
        String ownerFingerprint,
        String activeKeyId,
        String activeFingerprint,
        Instant activeCreatedAt,
        Set<String> acceptedKeyIds,
        String publicJwks,
        RotationPhase rotationPhase,
        String rotationFingerprint) {

    private static final Pattern KEY_ID = Pattern.compile("wk_[A-Za-z0-9_-]{32,64}");
    private static final Pattern FINGERPRINT = Pattern.compile("sha256:[a-f0-9]{64}");

    public RuntimeWorkloadCredentialState {
        if (credentialRef == null || !credentialRef.startsWith("credentialref://")) {
            throw new IllegalArgumentException("workload credential state requires a credentialref URI");
        }
        if (authenticationMethod == null) {
            throw new IllegalArgumentException("workload credential authentication method is required");
        }
        requireFingerprint(ownerFingerprint, "ownerFingerprint");
        if (activeKeyId == null || !KEY_ID.matcher(activeKeyId).matches()) {
            throw new IllegalArgumentException("activeKeyId has an invalid format");
        }
        requireFingerprint(activeFingerprint, "activeFingerprint");
        if (activeCreatedAt == null) {
            throw new IllegalArgumentException("activeCreatedAt is required");
        }
        acceptedKeyIds = acceptedKeyIds == null
                ? Set.of()
                : Set.copyOf(new LinkedHashSet<>(acceptedKeyIds));
        if (acceptedKeyIds.isEmpty() || !acceptedKeyIds.contains(activeKeyId)
                || acceptedKeyIds.stream().anyMatch(value -> !KEY_ID.matcher(value).matches())) {
            throw new IllegalArgumentException("acceptedKeyIds must contain only valid key ids and include the active key");
        }
        if (publicJwks == null || publicJwks.isBlank()) {
            throw new IllegalArgumentException("publicJwks is required");
        }
        if (rotationPhase == null) {
            throw new IllegalArgumentException("rotationPhase is required");
        }
        if (rotationPhase == RotationPhase.NONE) {
            if (rotationFingerprint != null || acceptedKeyIds.size() != 1) {
                throw new IllegalArgumentException("non-rotating credentials must expose one accepted key and no rotation fingerprint");
            }
        } else {
            requireFingerprint(rotationFingerprint, "rotationFingerprint");
            if (acceptedKeyIds.size() != 2) {
                throw new IllegalArgumentException("a credential rotation must expose exactly two accepted keys");
            }
        }
    }

    private static void requireFingerprint(String value, String field) {
        if (value == null || !FINGERPRINT.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a sha256 fingerprint");
        }
    }

    public enum RotationPhase {
        NONE,
        PREPARED,
        ACTIVE_OVERLAP
    }
}
