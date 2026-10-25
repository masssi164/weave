package com.massimotter.weave.backend.chat.domain;

public record ConversationId(String value) {

    public ConversationId {
        value = canonical(value, "conversation id");
    }

    private static String canonical(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 160
                || !value.matches("[a-zA-Z0-9][a-zA-Z0-9._/-]*")) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }
}
