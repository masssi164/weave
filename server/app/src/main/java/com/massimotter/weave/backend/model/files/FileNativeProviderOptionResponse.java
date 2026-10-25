package com.massimotter.weave.backend.model.files;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "One native OS file-provider setup option backed by the Weave files facade.")
public record FileNativeProviderOptionResponse(
        @Schema(description = "Target platform family.", example = "ios")
        String platform,
        @Schema(description = "Native OS boundary to implement.", example = "FileProviderExtension")
        String osBoundary,
        @Schema(description = "Flutter/native bridge role for setup, status, and revoke only.", example = "pigeon-or-platform-channel")
        String bridge,
        @Schema(description = "Whether this native provider option is ready for end-user setup.", example = "false")
        boolean available,
        @Schema(description = "Support-safe setup state.", example = "extension_contract_ready")
        String setupState,
        @Schema(description = "Support-safe setup action or route. Does not expose raw provider URLs.", example = "open-weave-files-native-setup")
        String setupAction,
        @Schema(description = "Native implementation contracts required before availability can be true.")
        List<String> requiredContracts,
        @Schema(description = "Support-safe notes for member/admin setup surfaces.")
        List<String> notes) {

    public FileNativeProviderOptionResponse {
        requiredContracts = requiredContracts == null ? List.of() : List.copyOf(requiredContracts);
        notes = notes == null ? List.of() : List.copyOf(notes);
    }
}
