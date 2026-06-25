package com.massimotter.weave.backend.model.chat;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Support-safe attachment policy metadata for Weave Chat.")
public record ChatAttachmentPolicyResponse(
        @Schema(description = "Whether message attachment references are supported by this facade.", example = "true",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean attachmentRefsSupported,
        @Schema(description = "Maximum attachment references accepted on one message.", example = "8",
                requiredMode = Schema.RequiredMode.REQUIRED)
        int maxAttachmentRefs,
        @Schema(description = "Whether raw provider media URLs are exposed to clients. Always false for release behavior.", example = "false",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean rawProviderMediaUrlsExposed) {
}
