package com.massimotter.weave.backend.model.interop;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SlackEventRequest(
        @NotBlank @Size(max = 128) String eventId,
        @NotBlank @Size(max = 128) String teamId,
        @NotBlank @Size(max = 128) String channelId,
        @NotBlank @Size(max = 128) String userId,
        @NotBlank @Size(max = 4096) String text,
        @Size(max = 128) String threadTs,
        @Size(max = 128) String subtype) {
}
