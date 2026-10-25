package com.massimotter.weave.backend.model.calendar;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "One native OS calendar setup/sync option backed by the Weave calendar facade.")
public record CalendarNativeSyncOptionResponse(
        @Schema(description = "Target platform family.", example = "ios")
        String platform,
        @Schema(description = "Native OS boundary to implement.", example = "CalDAVConfigurationProfile")
        String osBoundary,
        @Schema(description = "Flutter/native bridge role for setup, status, and revoke only.", example = "pigeon-or-platform-channel")
        String bridge,
        @Schema(description = "Whether this native sync option is ready for end-user setup.", example = "false")
        boolean available,
        @Schema(description = "Support-safe setup state.", example = "setup_contract_ready")
        String setupState,
        @Schema(description = "Support-safe setup action or route. Does not expose raw provider URLs.", example = "open-weave-calendar-native-setup")
        String setupAction,
        @Schema(description = "Native implementation contracts required before availability can be true.")
        List<String> requiredContracts,
        @Schema(description = "Support-safe notes for member/admin setup surfaces.")
        List<String> notes) {

    public CalendarNativeSyncOptionResponse {
        requiredContracts = requiredContracts == null ? List.of() : List.copyOf(requiredContracts);
        notes = notes == null ? List.of() : List.copyOf(notes);
    }
}
