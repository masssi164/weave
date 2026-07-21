package com.massimotter.weave.backend.agentruntime.port;

public final class StaleRuntimeCellException extends RuntimeException {
    public StaleRuntimeCellException(String message) {
        super(message);
    }
}
