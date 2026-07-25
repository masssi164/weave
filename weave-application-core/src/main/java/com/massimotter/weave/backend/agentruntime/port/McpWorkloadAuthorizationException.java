package com.massimotter.weave.backend.agentruntime.port;

/** Support-safe fail-closed classification for the private MCP bridge. */
public final class McpWorkloadAuthorizationException extends RuntimeException {
    private final boolean authorityUnavailable;

    public McpWorkloadAuthorizationException(boolean authorityUnavailable) {
        super(authorityUnavailable
                ? "The MCP workload authority is unavailable"
                : "The MCP workload is not currently authorized");
        this.authorityUnavailable = authorityUnavailable;
    }

    public boolean authorityUnavailable() {
        return authorityUnavailable;
    }
}
