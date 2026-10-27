package com.massimotter.weave.backend.security.workload.port;

/** Sanitized availability or protocol failure at the entitlement authority boundary. */
public final class WorkloadEntitlementAuthorityException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public WorkloadEntitlementAuthorityException(String message) {
        super(message);
    }

    public WorkloadEntitlementAuthorityException(String message, Throwable cause) {
        super(message, cause);
    }
}
