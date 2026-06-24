package com.massimotter.weave.backend.model.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Schema(description = "Canonical Weave Chat message. Provider event ids, room ids, and raw media URLs are deliberately omitted.")
public record ChatMessageResponse(
        @Schema(description = "Stable Weave message id.", example = "msg-001",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String id,
        @Schema(description = "Stable Weave conversation id.", example = "channel-general",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String conversationId,
        @Schema(description = "Weave principal reference, not a raw provider user id.", example = "user:alice",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String senderRef,
        @Schema(description = "Message text when readable through the Weave facade.")
        String text,
        @Schema(description = "Attachment references mediated by Weave product/file facades, never raw provider URLs.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        List<String> attachmentRefs,
        @Schema(description = "Whether this message belongs to the authenticated member, computed server-side from the Weave principal.", example = "true",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean isMine,
        @Schema(description = "Whether encrypted-provider content was redacted from this facade response.", example = "false",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean encryptedProviderContentRedacted,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        Instant sentAt,
        @Schema(description = "Support-safe delivery/runtime evidence. Raw provider ids, URLs, tokens, and diagnostics are deliberately omitted.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        Map<String, Object> deliveryEvidence) {
}
