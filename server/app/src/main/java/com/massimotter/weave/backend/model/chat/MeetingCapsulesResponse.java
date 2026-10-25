package com.massimotter.weave.backend.model.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Channel Meeting Capsule snapshot.")
public record MeetingCapsulesResponse(
        String conversationId,
        String contextId,
        @Schema(description = "Whether media controls are unavailable until a backend capability is configured.", example = "true")
        boolean failClosed,
        List<MeetingCapsuleResponse> capsules) {
}
