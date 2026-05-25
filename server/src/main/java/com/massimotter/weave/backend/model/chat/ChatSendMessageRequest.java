package com.massimotter.weave.backend.model.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "Explicit user action to send a canonical Weave Chat message.")
public record ChatSendMessageRequest(
        @NotBlank
        @Size(max = 4000)
        @Schema(description = "Message text to send through the Weave Chat facade.", example = "The release notes draft is ready for review.")
        String text,
        @Size(max = 8)
        @Schema(description = "Optional Weave attachment references. Raw provider media URLs are rejected.")
        List<@Size(max = 256) String> attachmentRefs) {

    public ChatSendMessageRequest {
        attachmentRefs = attachmentRefs == null ? List.of() : List.copyOf(attachmentRefs);
    }
}
