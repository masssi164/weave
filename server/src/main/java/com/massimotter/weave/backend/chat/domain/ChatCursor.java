package com.massimotter.weave.backend.chat.domain;

public record ChatCursor(String value) {

    public ChatCursor {
        if (value == null || value.isBlank() || value.length() > 255) {
            throw new IllegalArgumentException("chat cursor is invalid");
        }
        value = value.trim();
    }
}
