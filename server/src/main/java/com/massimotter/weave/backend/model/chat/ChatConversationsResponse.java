package com.massimotter.weave.backend.model.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Canonical Weave Chat conversation snapshot.")
public record ChatConversationsResponse(
        @Schema(description = "Product domain.", example = "chat")
        String domain,
        @Schema(description = "Release posture for this facade.", example = "canonical-domain-facade")
        String releaseStatus,
        @Schema(description = "Support-safe source label; not a raw provider name, URL, room id, or channel id.", example = "weave-chat-domain-facade")
        String source,
        ChatReadinessResponse readiness,
        List<ChatConversationResponse> conversations) {
}
