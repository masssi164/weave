package com.massimotter.weave.backend.agentruntime.port;

/** Support-safe fail-closed classification for the private MCP bridge. */
public final class McpWorkloadAuthorizationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final boolean authorityUnavailable;
    private final Reason reason;

    public McpWorkloadAuthorizationException(boolean authorityUnavailable) {
        this(authorityUnavailable, authorityUnavailable ? Reason.AUTHORITY_UNAVAILABLE : Reason.UNSPECIFIED);
    }

    public McpWorkloadAuthorizationException(boolean authorityUnavailable, Reason reason) {
        super(authorityUnavailable
                ? "The MCP workload authority is unavailable"
                : "The MCP workload is not currently authorized");
        this.authorityUnavailable = authorityUnavailable;
        this.reason = java.util.Objects.requireNonNull(reason, "reason");
    }

    public boolean authorityUnavailable() {
        return authorityUnavailable;
    }

    public String reasonCode() {
        return reason.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }

    public enum Reason {
        UNSPECIFIED,
        TOKEN_POLICY,
        TOKEN_LIFETIME,
        CELL_NOT_FOUND,
        CELL_BINDING,
        IDENTITY_BINDING,
        PROFILE_NOT_FOUND,
        PROFILE_INVALID,
        PROFILE_BINDING,
        ENTITLEMENT_NOT_FOUND,
        ENTITLEMENT_OBSERVATION,
        ENTITLEMENT_MISMATCH,
        TOOL_SCOPE,
        AUTHORITY_UNAVAILABLE
    }
}
