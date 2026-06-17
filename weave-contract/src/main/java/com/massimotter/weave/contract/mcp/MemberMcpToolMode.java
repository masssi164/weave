package com.massimotter.weave.contract.mcp;

public enum MemberMcpToolMode {
    READ(false), WRITE(true), EXTERNAL_SEND(true);
    private final boolean approvalRequiredByDefault;
    MemberMcpToolMode(boolean approvalRequiredByDefault) { this.approvalRequiredByDefault = approvalRequiredByDefault; }
    public boolean approvalRequiredByDefault() { return approvalRequiredByDefault; }
}
