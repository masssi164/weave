package com.massimotter.weave.backend.chat.domain;

import java.util.List;

public record ChatTimeline(
        String conversationId,
        List<ChatTimelineEvent> events) {

    public ChatTimeline {
        conversationId = new ConversationId(conversationId).value();
        events = events == null ? List.of() : List.copyOf(events);
    }
}
