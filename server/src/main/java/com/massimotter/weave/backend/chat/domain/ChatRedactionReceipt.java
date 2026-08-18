package com.massimotter.weave.backend.chat.domain;

import java.time.Instant;

/** Canonical acknowledgement for a redaction event distinct from its target. */
public record ChatRedactionReceipt(
        String redactionEventId,
        String targetEventId,
        String conversationId,
        String actorRef,
        Instant occurredAt) {

    public ChatRedactionReceipt {
        redactionEventId = required(redactionEventId, "chat redaction event", 255);
        targetEventId = required(targetEventId, "chat redaction target", 255);
        if (redactionEventId.equals(targetEventId)) {
            throw new IllegalArgumentException("chat redaction event must differ from its target");
        }
        conversationId = new ConversationId(conversationId).value();
        actorRef = new ChatActorRef(actorRef).value();
        if (occurredAt == null) {
            throw new IllegalArgumentException("chat redaction timestamp is required");
        }
    }

    private static String required(String value, String label, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException(label + " is invalid");
        }
        return value.trim();
    }
}
