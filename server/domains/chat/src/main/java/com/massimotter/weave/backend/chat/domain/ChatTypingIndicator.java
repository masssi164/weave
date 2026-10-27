package com.massimotter.weave.backend.chat.domain;

import java.time.Instant;

public record ChatTypingIndicator(
        String conversationId,
        String actorRef,
        boolean typing,
        Instant expiresAt) {

    public ChatTypingIndicator {
        conversationId = new ConversationId(conversationId).value();
        if (actorRef == null || actorRef.isBlank()) {
            throw new IllegalArgumentException("actorRef must not be blank");
        }
        actorRef = actorRef.trim();
        expiresAt = expiresAt == null ? Instant.now() : expiresAt;
    }
}
