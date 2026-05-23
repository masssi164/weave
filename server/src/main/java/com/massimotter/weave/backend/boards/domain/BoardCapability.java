package com.massimotter.weave.backend.boards.domain;

import com.fasterxml.jackson.annotation.JsonValue;

public enum BoardCapability {
    COMMENTS("comments"),
    ATTACHMENTS("attachments"),
    NON_DESTRUCTIVE_ARCHIVE("non_destructive_archive"),
    WEBHOOK_EVENTS("webhook_events"),
    INCREMENTAL_SYNC("incremental_sync"),
    CHECKLISTS("checklists"),
    CUSTOM_FIELDS("custom_fields"),
    ACCESSIBLE_NON_DRAG_MOVES("accessible_non_drag_moves");

    private final String contractName;

    BoardCapability(String contractName) {
        this.contractName = contractName;
    }

    @JsonValue
    public String contractName() {
        return contractName;
    }
}
