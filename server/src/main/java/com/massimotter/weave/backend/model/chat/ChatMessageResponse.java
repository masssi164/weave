package com.massimotter.weave.backend.model.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "Canonical Weave Chat message. Provider event ids, room ids, and raw media URLs are deliberately omitted.")
public record ChatMessageResponse(
        @Schema(description = "Stable Weave message id.", example = "msg-001")
        String id,
        @Schema(description = "Stable Weave conversation id.", example = "channel-general")
        String conversationId,
        @Schema(description = "Weave principal reference, not a raw provider user id.", example = "user:alice")
        String senderRef,
        @Schema(description = "Message text when readable through the Weave facade.")
        String text,
        @Schema(description = "Attachment references mediated by Weave product/file facades, never raw provider URLs.")
        List<String> attachmentRefs,
        @Schema(description = "Whether encrypted-provider content was redacted from this facade response.", example = "false")
        boolean encryptedProviderContentRedacted,
        Instant sentAt) {
}
