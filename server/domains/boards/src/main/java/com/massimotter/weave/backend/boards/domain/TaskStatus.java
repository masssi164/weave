package com.massimotter.weave.backend.boards.domain;

import com.fasterxml.jackson.annotation.JsonValue;

public enum TaskStatus {
    OPEN("open"),
    BLOCKED("blocked"),
    COMPLETED("completed"),
    ARCHIVED("archived");

    private final String contractName;

    TaskStatus(String contractName) {
        this.contractName = contractName;
    }

    @JsonValue
    public String contractName() {
        return contractName;
    }
}
