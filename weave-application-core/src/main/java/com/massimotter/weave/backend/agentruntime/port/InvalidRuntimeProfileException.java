package com.massimotter.weave.backend.agentruntime.port;

public final class InvalidRuntimeProfileException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String code;

    public InvalidRuntimeProfileException(String code) {
        super("RuntimeProfile validation failed: " + code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
