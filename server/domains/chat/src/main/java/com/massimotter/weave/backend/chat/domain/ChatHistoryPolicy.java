package com.massimotter.weave.backend.chat.domain;

import java.util.List;

public record ChatHistoryPolicy(
        String visibility,
        String retention,
        boolean exportAllowed,
        boolean redactOnMembershipRemoval,
        List<String> supportSafeNotes) {
    public ChatHistoryPolicy {
        supportSafeNotes = supportSafeNotes == null ? List.of() : List.copyOf(supportSafeNotes);
    }
}
