package com.massimotter.weave.backend.model.chat;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Canonical Weave Chat membership summary for the authenticated principal.")
public record ChatMembershipResponse(
        @Schema(description = "Weave principal reference, not a raw provider user id.", example = "user:alice")
        String principalRef,
        @Schema(description = "Conversation membership state.", example = "joined")
        String state,
        @Schema(description = "Conversation role in Weave vocabulary.", example = "member")
        String role) {
}
