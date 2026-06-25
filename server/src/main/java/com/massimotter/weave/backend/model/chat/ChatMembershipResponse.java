package com.massimotter.weave.backend.model.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Canonical Weave Chat membership summary for the authenticated principal.")
public record ChatMembershipResponse(
        @Schema(description = "Weave principal reference, not a raw provider user id.", example = "user:alice",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String principalRef,
        @Schema(description = "Conversation membership state.", example = "joined",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String state,
        @Schema(description = "Conversation role in Weave vocabulary.", example = "member",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String role) {
}
