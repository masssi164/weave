package com.massimotter.weave.backend.security.workload.port;

/** Support-safe denial from the authoritative entitlement adapter. */
public final class WorkloadEntitlementDeniedException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public WorkloadEntitlementDeniedException(String message) {
    super(message);
  }
}
