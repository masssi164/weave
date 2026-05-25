package com.massimotter.weave.backend.chat.domain;

import java.util.List;

public record ChatConversations(
        ChatReadiness readiness,
        List<ChatConversation> conversations) {
    public ChatConversations {
        conversations = conversations == null ? List.of() : List.copyOf(conversations);
    }
}
