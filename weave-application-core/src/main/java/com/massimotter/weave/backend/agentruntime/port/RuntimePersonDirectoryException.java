package com.massimotter.weave.backend.agentruntime.port;

public final class RuntimePersonDirectoryException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public RuntimePersonDirectoryException(String message) {
        super(message);
    }

    public RuntimePersonDirectoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
