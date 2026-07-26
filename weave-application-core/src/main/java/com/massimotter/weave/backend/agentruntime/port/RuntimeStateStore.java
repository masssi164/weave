package com.massimotter.weave.backend.agentruntime.port;

import com.massimotter.weave.backend.agentruntime.domain.RestoredRuntimeState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeStateGeneration;
import java.util.Optional;

/** Provider-neutral, CAS-governed external persistence for runtime-internal state. */
public interface RuntimeStateStore extends RuntimeStateStoreAdmin {
  RuntimeStateGeneration commit(CommitRuntimeStateCommand command);

  Optional<RestoredRuntimeState> current(ReadRuntimeStateCommand command);

  StoreReadiness readiness();

  record CommitRuntimeStateCommand(
      String organizationRef,
      String personRef,
      String cellRef,
      String runtimeStateStoreRef,
      long expectedGeneration,
      String runtimeProfileHash,
      byte[] state,
      String idempotencyKey,
      String auditRef) {
    public CommitRuntimeStateCommand {
      requireBinding(organizationRef, personRef, cellRef, runtimeStateStoreRef);
      if (expectedGeneration < 0) {
        throw new IllegalArgumentException("expectedGeneration cannot be negative");
      }
      if (runtimeProfileHash == null || !runtimeProfileHash.matches("sha256:[a-f0-9]{64}")) {
        throw new IllegalArgumentException("runtimeProfileHash must be a lowercase sha256 digest");
      }
      if (state == null) {
        throw new IllegalArgumentException("runtime state bytes are required");
      }
      state = state.clone();
      requireIdempotency(idempotencyKey);
      requireText(auditRef, "auditRef");
    }

    @Override
    public byte[] state() {
      return state.clone();
    }
  }

  record ReadRuntimeStateCommand(
      String organizationRef, String personRef, String cellRef, String runtimeStateStoreRef) {
    public ReadRuntimeStateCommand {
      requireBinding(organizationRef, personRef, cellRef, runtimeStateStoreRef);
    }
  }

  record StoreReadiness(boolean ready, String state, long generationCount) {
    public StoreReadiness {
      if (state == null || state.isBlank() || generationCount < 0) {
        throw new IllegalArgumentException("runtime-state readiness is invalid");
      }
    }
  }

  private static void requireBinding(
      String organizationRef, String personRef, String cellRef, String runtimeStateStoreRef) {
    requireText(organizationRef, "organizationRef");
    requireText(personRef, "personRef");
    requireText(cellRef, "cellRef");
    if (runtimeStateStoreRef == null || !runtimeStateStoreRef.startsWith("runtime-state://")) {
      throw new IllegalArgumentException("runtimeStateStoreRef must use runtime-state://");
    }
  }

  private static void requireIdempotency(String value) {
    if (value == null || value.length() < 16 || value.length() > 128) {
      throw new IllegalArgumentException("idempotency key length must be between 16 and 128");
    }
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
  }
}
