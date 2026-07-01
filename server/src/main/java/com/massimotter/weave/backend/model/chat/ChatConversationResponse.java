package com.massimotter.weave.backend.model.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

@Schema(description = "Canonical Weave Chat conversation. Provider-specific room/channel ids are deliberately omitted.")
public record ChatConversationResponse(
        @Schema(description = "Stable Weave conversation id.", example = "channel-general",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String id,
        @Schema(description = "Weave Context/Space id that owns the conversation.", example = "workspace-default",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String contextId,
        @Schema(description = "Conversation kind in Weave vocabulary.", example = "channel",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String kind,
        @Schema(description = "Member-facing conversation title.", example = "General workspace channel",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        ChatMembershipResponse membership,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        ChatHistoryPolicyResponse historyPolicy,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        ChatAttachmentPolicyResponse attachmentPolicy,
        @Schema(description = "Member-visible product actions available in this conversation.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        List<String> availableActions,
        @Schema(description = "Last canonical message timestamp, if any.")
        Instant lastMessageAt) {
}
