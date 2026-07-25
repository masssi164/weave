package com.massimotter.weave.backend.agentruntime.port;

/** Sanitized availability or protocol failure at the entitlement authority boundary. */
public final class RuntimeEntitlementAuthorityException extends RuntimeException {
    public RuntimeEntitlementAuthorityException(String message) {
        super(message);
    }

    public RuntimeEntitlementAuthorityException(String message, Throwable cause) {
        super(message, cause);
    }
}
