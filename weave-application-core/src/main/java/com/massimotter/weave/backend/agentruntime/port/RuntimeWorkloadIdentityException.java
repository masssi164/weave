package com.massimotter.weave.backend.agentruntime.port;

/** Support-safe provider/secret-boundary failure. */
public final class RuntimeWorkloadIdentityException extends RuntimeException {
    public RuntimeWorkloadIdentityException(String message) {
        super(message);
    }

    public RuntimeWorkloadIdentityException(String message, Throwable cause) {
        super(message, cause);
    }
}
