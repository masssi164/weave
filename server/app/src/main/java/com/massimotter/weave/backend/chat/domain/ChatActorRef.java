package com.massimotter.weave.backend.chat.domain;

public record ChatActorRef(String value) {

    public ChatActorRef {
        if (value == null || value.isBlank() || value.length() > 255) {
            throw new IllegalArgumentException("chat actor reference is invalid");
        }
        value = value.trim();
    }
}
