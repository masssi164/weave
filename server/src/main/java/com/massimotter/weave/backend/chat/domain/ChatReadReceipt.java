package com.massimotter.weave.backend.chat.domain;

import java.time.Instant;

public record ChatReadReceipt(
        String conversationId,
        String actorRef,
        String eventId,
        Instant readAt) {

    public ChatReadReceipt {
        conversationId = new ConversationId(conversationId).value();
        actorRef = required(actorRef, "actorRef");
        eventId = required(eventId, "eventId");
        readAt = readAt == null ? Instant.now() : readAt;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
