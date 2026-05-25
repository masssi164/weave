package com.massimotter.weave.backend.model.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Admin/operator request for a support-safe Chat provider replacement dry-run.")
public record ChatProviderReplacementDryRunRequest(
        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "[a-z0-9][a-z0-9-]{1,63}")
        @Schema(description = "Source chat adapter key, not a URL or credential-bearing identifier.", example = "slack")
        String sourceAdapter,
        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "[a-z0-9][a-z0-9-]{1,63}")
        @Schema(description = "Target chat adapter key, not a URL or credential-bearing identifier.", example = "synapse-homeserver")
        String targetAdapter,
        @Min(0) @Max(1000000)
        int conversationCount,
        @Min(0) @Max(10000000)
        int messageCount,
        @Min(0) @Max(1000000)
        int attachmentCount,
        @Min(0) @Max(1000000)
        int encryptedRoomCount,
        @Min(0) @Max(1000000)
        int identityConflictCount) {
}
