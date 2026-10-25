package com.massimotter.weave.backend.chat.domain;

import java.time.Instant;

public record ChatChange(
        long sequence,
        String kind,
        ConversationId conversationId,
        String messageId,
        Instant occurredAt) {

    public ChatChange {
        if (sequence < 1 || kind == null || kind.isBlank() || conversationId == null || occurredAt == null) {
            throw new IllegalArgumentException("canonical chat change is invalid");
        }
    }
}
