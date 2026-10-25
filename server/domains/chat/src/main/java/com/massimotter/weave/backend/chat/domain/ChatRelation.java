package com.massimotter.weave.backend.chat.domain;

public record ChatRelation(
        String kind,
        String targetEventId,
        String replyToEventId) {

    public ChatRelation {
        kind = requireText(kind, "relation kind");
        targetEventId = requireText(targetEventId, "relation target");
        replyToEventId = optionalText(replyToEventId);
        if (!kind.matches("reply|thread|replace|reaction")) {
            throw new IllegalArgumentException("chat relation kind is invalid");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 255) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value.trim();
    }

    private static String optionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requireText(value, "reply event id");
    }
}
