package com.massimotter.weave.backend.provider;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ProviderState {
    DISABLED("disabled"),
    NOT_CONFIGURED("not_configured"),
    CONFIGURED("configured"),
    READY("ready"),
    DEGRADED("degraded"),
    UNSUPPORTED("unsupported");

    private final String contractName;

    ProviderState(String contractName) {
        this.contractName = contractName;
    }

    @JsonValue
    public String contractName() {
        return contractName;
    }
}
