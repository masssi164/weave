package com.massimotter.weave.backend.model.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "Canonical Weave Chat messages for one conversation.")
public record ChatMessagesResponse(
        @Schema(description = "Stable Weave conversation id.", example = "channel-general",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String conversationId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        ChatReadinessResponse readiness,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        List<ChatMessageResponse> messages) {
}
