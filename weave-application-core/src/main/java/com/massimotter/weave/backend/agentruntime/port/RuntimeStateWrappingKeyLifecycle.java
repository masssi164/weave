package com.massimotter.weave.backend.agentruntime.port;

import java.time.Instant;
import java.util.List;

/** Explicit offline lifecycle for the self-hosted runtime-state wrapping-key root. */
public interface RuntimeStateWrappingKeyLifecycle {
    KeyRingState initialize(String operationRef);

    KeyRingState rotate(String operationRef);

    KeyRingState current();

    record KeyRingState(
            String activeKeyRef,
            String lastOperationRefHash,
            List<KeyState> keys) {
        public KeyRingState {
            if (activeKeyRef == null || activeKeyRef.isBlank()
                    || lastOperationRefHash == null
                    || !lastOperationRefHash.matches("sha256:[a-f0-9]{64}")
                    || keys == null || keys.isEmpty()) {
                throw new IllegalArgumentException("runtime-state wrapping-key ring is incomplete");
            }
            keys = List.copyOf(keys);
        }
    }

    record KeyState(String keyRef, Status status, Instant activatedAt, Instant overlapStartedAt) {
        public KeyState {
            if (keyRef == null || keyRef.isBlank() || status == null || activatedAt == null) {
                throw new IllegalArgumentException("runtime-state wrapping-key metadata is incomplete");
            }
            if (status == Status.OVERLAP && overlapStartedAt == null) {
                throw new IllegalArgumentException("overlap key requires an overlap start time");
            }
            if (status == Status.ACTIVE && overlapStartedAt != null) {
                throw new IllegalArgumentException("active key cannot have an overlap start time");
            }
        }
    }

    enum Status {
        ACTIVE,
        OVERLAP
    }
}
