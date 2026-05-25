package com.massimotter.weave.backend.model.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Canonical Weave Chat messages for one conversation.")
public record ChatMessagesResponse(
        @Schema(description = "Stable Weave conversation id.", example = "channel-general")
        String conversationId,
        ChatReadinessResponse readiness,
        List<ChatMessageResponse> messages) {
}
