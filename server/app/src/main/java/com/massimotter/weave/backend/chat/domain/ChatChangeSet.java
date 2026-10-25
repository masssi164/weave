package com.massimotter.weave.backend.chat.domain;

import java.util.List;

public record ChatChangeSet(ChatCursor cursor, List<ChatChange> changes) {

    public ChatChangeSet {
        if (cursor == null) {
            throw new IllegalArgumentException("chat change set cursor is required");
        }
        changes = changes == null ? List.of() : List.copyOf(changes);
    }
}
