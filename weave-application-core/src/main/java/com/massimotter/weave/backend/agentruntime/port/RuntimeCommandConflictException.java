package com.massimotter.weave.backend.agentruntime.port;

public final class RuntimeCommandConflictException extends RuntimeException {
    public RuntimeCommandConflictException(String message) {
        super(message);
    }
}
