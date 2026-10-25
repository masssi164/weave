package com.massimotter.weave.backend.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

@Schema(description = "Partial product profile update. Null fields leave the current value unchanged.")
public record UpdateProductProfileRequest(
        @Schema(description = "User-visible product display name.", example = "Alice Example")
        @Size(max = 120)
        String displayName,
        @Schema(description = "Backend-managed avatar reference.", example = "weave-avatar://user/alice")
        @Size(max = 2048)
        String avatar,
        @Schema(description = "Preferred BCP 47 locale tag.", example = "en")
        @Size(max = 35)
        @Pattern(regexp = "[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*", message = "must be a valid BCP 47 locale tag")
        String locale,
        @Schema(description = "Preferred IANA timezone.", example = "Europe/Berlin")
        @Size(max = 64)
        String timezone,
        @Schema(description = "Frontend accessibility preferences. Keep keys and values support-safe and non-secret.")
        @Size(max = 20)
        Map<@Size(max = 64) String, @Size(max = 256) String> accessibilityPreferences,
        @Schema(description = "Profile visibility policy for product surfaces.", allowableValues = {"private", "workspace", "public"})
        @Pattern(regexp = "private|workspace|public", message = "must be one of private, workspace, or public")
        String profileVisibility) {
}
