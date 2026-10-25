package com.massimotter.weave.backend.boards.domain;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ColumnSemanticStatus {
    NOT_STARTED("not_started"),
    IN_PROGRESS("in_progress"),
    BLOCKED("blocked"),
    DONE("done"),
    ARCHIVED("archived");

    private final String contractName;

    ColumnSemanticStatus(String contractName) {
        this.contractName = contractName;
    }

    @JsonValue
    public String contractName() {
        return contractName;
    }
}
