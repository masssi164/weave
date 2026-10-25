package com.massimotter.weave.backend.model.interop;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SlackOutboundMessageRequest(
        @NotBlank @Size(max = 128) String eventId,
        @NotBlank @Size(max = 4096) String text,
        @Size(max = 128) String threadTs) {
}
