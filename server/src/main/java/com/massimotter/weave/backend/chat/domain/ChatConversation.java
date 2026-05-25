package com.massimotter.weave.backend.chat.domain;

import java.time.Instant;
import java.util.List;

public record ChatConversation(
        String conversationId,
        String title,
        String kind,
        ChatMemberState state,
        String memberImpact,
        Instant updatedAt,
        ChatHistoryPolicy historyPolicy,
        List<ChatMembership> memberships,
        List<ChatAttachmentMetadata> recentAttachments) {
    public ChatConversation {
        memberships = memberships == null ? List.of() : List.copyOf(memberships);
        recentAttachments = recentAttachments == null ? List.of() : List.copyOf(recentAttachments);
    }
}
