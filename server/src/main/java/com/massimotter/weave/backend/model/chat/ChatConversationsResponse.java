package com.massimotter.weave.backend.model.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "Canonical Weave Chat conversation snapshot.")
public record ChatConversationsResponse(
        @Schema(description = "Product domain.", example = "chat",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String domain,
        @Schema(description = "Release posture for this facade.", example = "canonical-domain-facade",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String releaseStatus,
        @Schema(description = "Support-safe source label; not a raw provider name, URL, room id, or channel id.", example = "weave-chat-domain-facade",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String source,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        ChatReadinessResponse readiness,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        List<ChatConversationResponse> conversations) {
}
