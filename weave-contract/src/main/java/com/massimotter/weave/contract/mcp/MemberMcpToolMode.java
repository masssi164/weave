package com.massimotter.weave.contract.mcp;

public enum MemberMcpToolMode {
    READ(false), WRITE(true), EXTERNAL_SEND(true);
    private final boolean approvalRequiredByDefault;
    MemberMcpToolMode(boolean approvalRequiredByDefault) { this.approvalRequiredByDefault = approvalRequiredByDefault; }
    public boolean approvalRequiredByDefault() { return approvalRequiredByDefault; }

    public WeaveMcpToolMode toBridgeMode() { return WeaveMcpToolMode.valueOf(name()); }
    public static MemberMcpToolMode fromBridgeMode(WeaveMcpToolMode mode) { return MemberMcpToolMode.valueOf(mode.name()); }
}
