package com.massimotter.weave.backend.chat.domain;

/** Signals that a private provider callback raced a canonical acknowledgement and must be retried. */
public final class ChatCallbackRetryRequiredException extends RuntimeException {

    public ChatCallbackRetryRequiredException() {
        super("Canonical Chat callback reconciliation is pending.");
    }
}
