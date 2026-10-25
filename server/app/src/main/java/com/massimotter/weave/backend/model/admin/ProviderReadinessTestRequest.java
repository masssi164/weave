package com.massimotter.weave.backend.model.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Admin request to exercise a support-safe provider readiness contract.")
public record ProviderReadinessTestRequest(
        @NotBlank
        @Schema(example = "nextcloud-files")
        String providerKey,
        @Schema(example = "readiness")
        String testKind,
        @Schema(description = "Optional SecretRef id. Raw secret values are forbidden and ignored/redacted by the server.")
        String secretRef) {
}
