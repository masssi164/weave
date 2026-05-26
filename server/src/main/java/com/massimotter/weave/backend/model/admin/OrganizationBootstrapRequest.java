package com.massimotter.weave.backend.model.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "Admin-owned first-use organization bootstrap contract for existing or newly provisioned organizations.")
public record OrganizationBootstrapRequest(
        @NotBlank
        @Schema(description = "Weave organization id to bootstrap or bind.", example = "acme-prod")
        String organizationId,
        @Schema(description = "Existing organization binds an already-owned tenant; new organization provisions initial policy.")
        String bootstrapMode,
        @Schema(description = "Immutable issuer+subject keys that retain owner/admin recovery access after bootstrap.")
        @Size(max = 25)
        List<@NotBlank @Size(max = 512) @Pattern(
                regexp = "issuer\\+subject:[^#\\s]{1,384}#[^#\\s]{1,128}",
                message = "must be a support-safe issuer+subject key") String> adminSubjectKeys,
        @Schema(description = "Support-safe reason recorded in audit.")
        String reason) {

    public OrganizationBootstrapRequest {
        adminSubjectKeys = adminSubjectKeys == null ? List.of() : List.copyOf(adminSubjectKeys);
    }
}
