package com.massimotter.weave.backend.agentruntime.port;

public final class RuntimePolicyException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public RuntimePolicyException(String message) {
        super(message);
    }

    public RuntimePolicyException(String message, Throwable cause) {
        super(message, cause);
    }
}
