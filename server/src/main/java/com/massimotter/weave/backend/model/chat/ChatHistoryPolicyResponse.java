package com.massimotter.weave.backend.model.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Support-safe chat history policy metadata for a canonical Weave conversation.")
public record ChatHistoryPolicyResponse(
        @Schema(description = "Stable policy key, not a raw provider room or channel identifier.", example = "workspace-default-history",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String policyKey,
        @Schema(description = "Member-visible history visibility.", example = "joined-members",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String visibility,
        @Schema(description = "Whether backend-held chat history is readable through this facade.", example = "true",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean backendReadable,
        @Schema(description = "Whether encrypted-provider content is intentionally hidden from the backend facade.", example = "true",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean encryptedProviderContentRedacted) {
}
