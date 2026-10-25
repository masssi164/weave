package com.massimotter.weave.backend.chat.domain;

public final class ChatAccessDeniedException extends RuntimeException {

    public ChatAccessDeniedException() {
        super("The Chat operation is not permitted for this conversation.");
    }
}
