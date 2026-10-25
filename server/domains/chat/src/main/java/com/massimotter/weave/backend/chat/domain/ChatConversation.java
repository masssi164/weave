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
        ChatEncryptionState encryptionState,
        ChatHistoryPolicy historyPolicy,
        List<ChatMembership> memberships,
        List<ChatAttachmentMetadata> recentAttachments) {
    public ChatConversation {
        conversationId = new ConversationId(conversationId).value();
        encryptionState = encryptionState == null ? ChatEncryptionState.unencrypted() : encryptionState;
        memberships = memberships == null ? List.of() : List.copyOf(memberships);
        recentAttachments = recentAttachments == null ? List.of() : List.copyOf(recentAttachments);
    }
}
