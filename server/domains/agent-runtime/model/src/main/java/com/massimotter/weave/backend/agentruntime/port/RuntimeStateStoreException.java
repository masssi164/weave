package com.massimotter.weave.backend.agentruntime.port;

public final class RuntimeStateStoreException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public RuntimeStateStoreException(String message) {
        super(message);
    }

    public RuntimeStateStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
