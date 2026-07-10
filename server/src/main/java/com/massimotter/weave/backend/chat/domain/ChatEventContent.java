package com.massimotter.weave.backend.chat.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public record ChatEventContent(
        ChatEventKind kind,
        String messageType,
        String body,
        String format,
        String formattedBody,
        ChatRelation relation,
        String reactionKey,
        Map<String, Object> presentationExtensions) {

    private static final Set<String> ALLOWED_PRESENTATION_EXTENSIONS = Set.of(
            "com.openclaw.approval",
            "com.openclaw.presentation");

    public ChatEventContent {
        if (kind == null) {
            throw new IllegalArgumentException("chat event kind is required");
        }
        messageType = optionalText(messageType, 64);
        body = optionalText(body, 65_536);
        format = optionalText(format, 128);
        formattedBody = optionalText(formattedBody, 131_072);
        reactionKey = optionalText(reactionKey, 128);
        presentationExtensions = copyExtensions(presentationExtensions);
        if (kind == ChatEventKind.MESSAGE && body == null) {
            throw new IllegalArgumentException("chat message body is required");
        }
        if (kind == ChatEventKind.REACTION
                && (reactionKey == null || relation == null || !"reaction".equals(relation.kind()))) {
            throw new IllegalArgumentException("chat reaction target and key are required");
        }
    }

    public static ChatEventContent text(String body) {
        return new ChatEventContent(
                ChatEventKind.MESSAGE,
                "m.text",
                body,
                null,
                null,
                null,
                null,
                Map.of());
    }

    private static Map<String, Object> copyExtensions(Map<String, Object> extensions) {
        if (extensions == null || extensions.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : extensions.entrySet()) {
            if (!ALLOWED_PRESENTATION_EXTENSIONS.contains(entry.getKey())) {
                throw new IllegalArgumentException("chat presentation extension is not allowed");
            }
            safe.put(entry.getKey(), entry.getValue());
        }
        return Map.copyOf(safe);
    }

    private static String optionalText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException("chat event text is too long");
        }
        return value.trim();
    }
}
