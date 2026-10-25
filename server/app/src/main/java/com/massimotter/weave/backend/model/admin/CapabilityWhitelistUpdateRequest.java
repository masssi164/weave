package com.massimotter.weave.backend.model.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

@Schema(description = "Contract request for admin-owned capability whitelist/profile updates.")
public record CapabilityWhitelistUpdateRequest(
        @NotBlank
        @Schema(example = "workspace-admin")
        String profileKey,
        @Schema(example = "chat.read")
        List<String> capabilityKeys,
        @Schema(description = "Support-safe reason recorded in audit, without raw provider diagnostics or secrets.")
        String reason) {
    public CapabilityWhitelistUpdateRequest {
        capabilityKeys = capabilityKeys == null ? List.of() : List.copyOf(capabilityKeys);
    }
}
