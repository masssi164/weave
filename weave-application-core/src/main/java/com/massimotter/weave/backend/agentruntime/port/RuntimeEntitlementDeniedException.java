package com.massimotter.weave.backend.agentruntime.port;

/** Support-safe denial from the authoritative entitlement adapter. */
public final class RuntimeEntitlementDeniedException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public RuntimeEntitlementDeniedException(String message) {
    super(message);
  }
}
