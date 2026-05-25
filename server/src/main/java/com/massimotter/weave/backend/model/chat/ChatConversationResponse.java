package com.massimotter.weave.backend.model.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Canonical Weave Chat conversation. Provider-specific room/channel ids are deliberately omitted.")
public record ChatConversationResponse(
        @Schema(description = "Stable Weave conversation id.", example = "channel-general")
        String id,
        @Schema(description = "Weave Context/Space id that owns the conversation.", example = "workspace-default")
        String contextId,
        @Schema(description = "Conversation kind in Weave vocabulary.", example = "channel")
        String kind,
        @Schema(description = "Member-facing conversation title.", example = "General workspace channel")
        String title,
        ChatMembershipResponse membership,
        ChatHistoryPolicyResponse historyPolicy,
        ChatAttachmentPolicyResponse attachmentPolicy,
        @Schema(description = "Last canonical message timestamp, if any.")
        Instant lastMessageAt) {
}
