package com.massimotter.weave.backend.chat.domain;

import java.time.Duration;
import java.time.Instant;

public final class ChatProviderUnavailableException extends IllegalStateException {

    private final String supportSafeCode;
    private final Instant retryAt;

    public ChatProviderUnavailableException(String supportSafeCode) {
        this(supportSafeCode, null);
    }

    public ChatProviderUnavailableException(String supportSafeCode, Instant retryAt) {
        super("Weave Chat is temporarily unavailable.");
        this.supportSafeCode = supportSafeCode == null || supportSafeCode.isBlank()
                ? "chat-provider-unavailable"
                : supportSafeCode;
        this.retryAt = retryAt;
    }

    public String supportSafeCode() {
        return supportSafeCode;
    }

    public boolean throttled() {
        return "chat-provider-throttled".equals(supportSafeCode);
    }

    public long retryAfterMilliseconds(Instant now) {
        if (retryAt == null) {
            return 60_000;
        }
        return Math.max(1_000, Duration.between(now, retryAt).toMillis());
    }
}
