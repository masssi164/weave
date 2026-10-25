package com.massimotter.weave.backend.security.workload.port;

/** Support-safe provider/secret-boundary failure. */
public final class WorkloadIdentityException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public WorkloadIdentityException(String message) {
        super(message);
    }

    public WorkloadIdentityException(String message, Throwable cause) {
        super(message, cause);
    }
}
