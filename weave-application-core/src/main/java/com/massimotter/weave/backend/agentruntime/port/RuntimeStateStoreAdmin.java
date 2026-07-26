package com.massimotter.weave.backend.agentruntime.port;

/** Deletes runtime-internal state only; canonical collaboration content is never in this store. */
public interface RuntimeStateStoreAdmin {
  void deleteRuntimeState(DeleteRuntimeStateCommand command);

  record DeleteRuntimeStateCommand(
      String organizationRef,
      String personRef,
      String cellRef,
      String runtimeStateStoreRef,
      String idempotencyKey,
      String auditRef) {
    public DeleteRuntimeStateCommand {
      requireText(organizationRef, "organizationRef");
      requireText(personRef, "personRef");
      requireText(cellRef, "cellRef");
      if (runtimeStateStoreRef == null || !runtimeStateStoreRef.startsWith("runtime-state://")) {
        throw new IllegalArgumentException("runtimeStateStoreRef must use runtime-state://");
      }
      if (idempotencyKey == null || idempotencyKey.length() < 16 || idempotencyKey.length() > 128) {
        throw new IllegalArgumentException("idempotency key length must be between 16 and 128");
      }
      requireText(auditRef, "auditRef");
    }

    private static void requireText(String value, String field) {
      if (value == null || value.isBlank()) {
        throw new IllegalArgumentException(field + " is required");
      }
    }
  }
}
