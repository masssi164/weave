package com.massimotter.weave.backend.model.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

@Schema(description = "Admin-owned first-use organization bootstrap contract for existing or newly provisioned organizations.")
public record OrganizationBootstrapRequest(
        @NotBlank
        @Schema(description = "Weave organization id to bootstrap or bind.", example = "acme-prod")
        String organizationId,
        @Schema(description = "Existing organization binds an already-owned tenant; new organization provisions initial policy.")
        String bootstrapMode,
        @Schema(description = "Immutable issuer+subject keys that retain owner/admin recovery access after bootstrap.")
        List<String> adminSubjectKeys,
        @Schema(description = "Support-safe reason recorded in audit.")
        String reason) {

    public OrganizationBootstrapRequest {
        adminSubjectKeys = adminSubjectKeys == null ? List.of() : List.copyOf(adminSubjectKeys);
    }
}
