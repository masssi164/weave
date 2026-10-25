package com.massimotter.weave.backend.chat.domain;

import java.time.Instant;

/** Pending member metadata, distinct from an authorized joined resource or its history. */
public record ChatInvitation(
        String conversationId,
        String title,
        Instant updatedAt,
        ChatEncryptionState encryptionState,
        ChatActorRef inviterRef,
        ChatMembership membership) {
    public ChatInvitation {
        conversationId = new ConversationId(conversationId).value();
        if (title == null || title.length() > 512 || encryptionState == null || inviterRef == null || membership == null
                || !conversationId.equals(membership.conversationId())
                || !"invited".equals(membership.state()) || !membership.capabilities().isEmpty()) {
            throw new IllegalArgumentException("Canonical invitation metadata is invalid");
        }
    }
}
