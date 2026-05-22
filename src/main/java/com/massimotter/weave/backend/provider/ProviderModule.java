package com.massimotter.weave.backend.provider;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ProviderModule {
    IDENTITY_REALM("identity-realm"),
    FILES("files"),
    OFFICE("office"),
    CALENDAR("calendar"),
    CONTACTS("contacts"),
    FORMS("forms"),
    BOARDS("boards"),
    SOURCE_CONTROL("source-control"),
    CI("ci"),
    ISSUE_TRACKER("issue-tracker"),
    RELEASE("release");

    private final String contractName;

    ProviderModule(String contractName) {
        this.contractName = contractName;
    }

    @JsonValue
    public String contractName() {
        return contractName;
    }
}
