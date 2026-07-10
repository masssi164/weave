package com.massimotter.weave.backend.chat.domain;

import java.time.Instant;

public record ChatTimelineEvent(
        String eventId,
        String conversationId,
        String senderRef,
        Instant occurredAt,
        ChatEventContent content,
        String deliveryState,
        boolean redacted) {

    public ChatTimelineEvent {
        if (eventId == null || eventId.isBlank() || eventId.length() > 255) {
            throw new IllegalArgumentException("chat event id is invalid");
        }
        conversationId = new ConversationId(conversationId).value();
        senderRef = new ChatActorRef(senderRef).value();
        if (occurredAt == null || content == null) {
            throw new IllegalArgumentException("chat event timestamp and content are required");
        }
        deliveryState = deliveryState == null || deliveryState.isBlank()
                ? "sent"
                : deliveryState.trim();
    }

    public ChatTimelineEvent redact() {
        return new ChatTimelineEvent(
                eventId,
                conversationId,
                senderRef,
                occurredAt,
                content,
                deliveryState,
                true);
    }
}
