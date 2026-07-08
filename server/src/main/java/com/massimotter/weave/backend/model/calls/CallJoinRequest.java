package com.massimotter.weave.backend.model.calls;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Request a short-lived Weave call join grant.")
public record CallJoinRequest(
        @Schema(description = "Requested call role.", example = "participant")
        @Size(max = 32)
        String role) {
}
