package com.massimotter.weave.contract.mcp;

public enum WeaveMcpToolMode {
    READ(false), WRITE(true), EXTERNAL_SEND(true);
    private final boolean approvalRequiredByDefault;
    WeaveMcpToolMode(boolean approvalRequiredByDefault) { this.approvalRequiredByDefault = approvalRequiredByDefault; }
    public boolean approvalRequiredByDefault() { return approvalRequiredByDefault; }
}
