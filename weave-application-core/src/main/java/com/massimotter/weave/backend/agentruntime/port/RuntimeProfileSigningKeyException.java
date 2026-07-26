package com.massimotter.weave.backend.agentruntime.port;

public final class RuntimeProfileSigningKeyException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public RuntimeProfileSigningKeyException(String message) {
        super(message);
    }

    public RuntimeProfileSigningKeyException(String message, Throwable cause) {
        super(message, cause);
    }
}
