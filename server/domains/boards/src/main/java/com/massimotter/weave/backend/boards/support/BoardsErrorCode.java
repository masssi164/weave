package com.massimotter.weave.backend.boards.support;

public enum BoardsErrorCode {
    UNAUTHORIZED("unauthorized"),
    FORBIDDEN("forbidden"),
    NOT_FOUND("not_found"),
    CONFLICT("conflict"),
    RATE_LIMITED("rate_limited"),
    OFFLINE("offline"),
    VALIDATION("validation"),
    PROVIDER_UNAVAILABLE("provider_unavailable"),
    UNKNOWN("unknown"),
    UNSUPPORTED_CAPABILITY("unsupported_capability");

    private final String contractName;

    BoardsErrorCode(String contractName) {
        this.contractName = contractName;
    }

    public String contractName() {
        return contractName;
    }
}
