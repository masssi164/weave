package com.massimotter.weave.backend.chat.domain;

public record ChatTransactionId(String value) {

    public ChatTransactionId {
        if (value == null || value.isBlank() || value.length() > 160
                || !value.matches("[a-zA-Z0-9][a-zA-Z0-9._~-]*")) {
            throw new IllegalArgumentException("chat transaction id is invalid");
        }
        value = value.trim();
    }
}
