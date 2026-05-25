package com.massimotter.weave.backend.chat.domain;

import java.time.Instant;
import java.util.List;

public record ChatMembership(
        String membershipId,
        String conversationId,
        String memberRef,
        String role,
        String state,
        Instant joinedAt,
        List<String> capabilities) {
    public ChatMembership {
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
    }
}
