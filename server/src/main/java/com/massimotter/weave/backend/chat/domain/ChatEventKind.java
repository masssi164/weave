package com.massimotter.weave.backend.chat.domain;

public enum ChatEventKind {
    MESSAGE("message"),
    REACTION("reaction");

    private final String value;

    ChatEventKind(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
