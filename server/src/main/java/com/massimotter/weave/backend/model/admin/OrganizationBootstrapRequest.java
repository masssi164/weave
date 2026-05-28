package com.massimotter.weave.backend.model.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.massimotter.weave.backend.model.IdentityKeyFormat.MAX_PRIMARY_IDENTITY_KEY_LENGTH;
import static com.massimotter.weave.backend.model.IdentityKeyFormat.PRIMARY_IDENTITY_KEY_PATTERN;

@Schema(description = "Admin-owned first-use organization bootstrap contract for existing or newly provisioned organizations.")
public record OrganizationBootstrapRequest(
        @NotBlank
        @Schema(description = "Weave organization id to bootstrap or bind.", example = "acme-prod")
        String organizationId,
        @Schema(description = "Existing organization binds an already-owned tenant; new organization provisions initial policy.")
        String bootstrapMode,
        @Schema(description = "Immutable issuer+subject keys that retain owner/admin recovery access after bootstrap.")
        @Size(max = 25)
        List<@NotBlank @Size(max = MAX_PRIMARY_IDENTITY_KEY_LENGTH) @Pattern(
                regexp = PRIMARY_IDENTITY_KEY_PATTERN,
                message = "must be a support-safe issuer+subject key") String> adminPrimaryIdentityKeys,
        @Schema(description = "Support-safe reason recorded in audit.")
        String reason) {

    public OrganizationBootstrapRequest {
        adminPrimaryIdentityKeys = adminPrimaryIdentityKeys == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(adminPrimaryIdentityKeys));
    }
}
