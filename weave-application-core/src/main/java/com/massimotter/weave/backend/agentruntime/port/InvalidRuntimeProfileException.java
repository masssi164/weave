package com.massimotter.weave.backend.agentruntime.port;

public final class InvalidRuntimeProfileException extends RuntimeException {
    private final String code;

    public InvalidRuntimeProfileException(String code) {
        super("RuntimeProfile validation failed: " + code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
