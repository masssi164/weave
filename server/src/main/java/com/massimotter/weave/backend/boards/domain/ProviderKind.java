package com.massimotter.weave.backend.boards.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Provider identity used only for diagnostics, export, migration, and sync metadata.
 * User-facing Boards language stays Weave-owned and provider-neutral.
 */
public enum ProviderKind {
    VIKUNJA("vikunja"),
    OPEN_PROJECT("openproject"),
    NEXTCLOUD_DECK("nextcloud-deck"),
    IN_MEMORY("in-memory"),
    UNKNOWN("unknown");

    private final String contractName;

    ProviderKind(String contractName) {
        this.contractName = contractName;
    }

    @JsonValue
    public String contractName() {
        return contractName;
    }
}
