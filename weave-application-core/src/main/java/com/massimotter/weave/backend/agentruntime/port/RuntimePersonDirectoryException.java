package com.massimotter.weave.backend.agentruntime.port;

public final class RuntimePersonDirectoryException extends RuntimeException {
    public RuntimePersonDirectoryException(String message) {
        super(message);
    }

    public RuntimePersonDirectoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
