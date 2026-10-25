package com.massimotter.weave.backend.chat.domain;

import java.util.List;

public record ChatMessages(
        ChatReadiness readiness,
        String conversationId,
        List<ChatMessage> messages) {
    public ChatMessages {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
