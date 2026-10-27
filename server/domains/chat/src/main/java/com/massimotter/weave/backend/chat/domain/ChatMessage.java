package com.massimotter.weave.backend.chat.domain;

import java.time.Instant;
import java.util.List;

public record ChatMessage(
        String messageId,
        String conversationId,
        String senderRef,
        Instant sentAt,
        String body,
        String deliveryState,
        List<ChatAttachmentMetadata> attachments) {
    public ChatMessage {
        conversationId = new ConversationId(conversationId).value();
        if (messageId == null || messageId.isBlank() || messageId.length() > 200) {
            throw new IllegalArgumentException("message id is invalid");
        }
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }
}
