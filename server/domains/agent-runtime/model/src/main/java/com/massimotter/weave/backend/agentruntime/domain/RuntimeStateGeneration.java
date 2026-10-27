package com.massimotter.weave.backend.agentruntime.domain;

import java.time.Instant;

/** Secret-free metadata for one committed external runtime-state generation. */
public record RuntimeStateGeneration(
        String generationRef,
        String runtimeStateStoreRef,
        long generation,
        String runtimeProfileHash,
        long plaintextBytes,
        int chunkCount,
        String wrappingKeyRef,
        Instant committedAt) {

    public RuntimeStateGeneration {
        requireText(generationRef, "generationRef");
        if (!generationRef.startsWith("state-generation:")) {
            throw new IllegalArgumentException("generationRef must use state-generation:");
        }
        if (runtimeStateStoreRef == null || !runtimeStateStoreRef.startsWith("runtime-state://")) {
            throw new IllegalArgumentException("runtimeStateStoreRef must use runtime-state://");
        }
        if (generation < 1) {
            throw new IllegalArgumentException("generation must be positive");
        }
        if (runtimeProfileHash == null || !runtimeProfileHash.matches("sha256:[a-f0-9]{64}")) {
            throw new IllegalArgumentException("runtimeProfileHash must be a lowercase sha256 digest");
        }
        if (plaintextBytes < 0 || chunkCount < 1) {
            throw new IllegalArgumentException("runtime state size and chunk count are invalid");
        }
        requireText(wrappingKeyRef, "wrappingKeyRef");
        if (committedAt == null) {
            throw new IllegalArgumentException("committedAt is required");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
