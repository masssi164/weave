package com.massimotter.weave.backend.agentruntime.port;

public final class RuntimePolicyException extends RuntimeException {
    public RuntimePolicyException(String message) {
        super(message);
    }

    public RuntimePolicyException(String message, Throwable cause) {
        super(message, cause);
    }
}
