package com.massimotter.weave.mcp;

final class McpAdmissionException extends RuntimeException {
    enum Kind {
        FORBIDDEN,
        INSUFFICIENT_SCOPE,
        BAD_REQUEST,
        UNAVAILABLE
    }

    private final Kind kind;

    McpAdmissionException(Kind kind) {
        super("The MCP request was rejected by the workload boundary");
        this.kind = kind;
    }

    Kind kind() {
        return kind;
    }
}
