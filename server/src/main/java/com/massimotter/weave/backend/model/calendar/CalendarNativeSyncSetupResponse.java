package com.massimotter.weave.backend.model.calendar;

import com.massimotter.weave.backend.model.WorkspaceCapabilityStatusResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Support-safe native Calendar setup/sync contract for OS calendar accounts.")
public record CalendarNativeSyncSetupResponse(
        @Schema(description = "Calendar capability readiness as seen by the authenticated member.")
        WorkspaceCapabilityStatusResponse readiness,
        @Schema(description = "True when this setup contract excludes raw provider endpoints, credentials, and diagnostics.",
                example = "true")
        boolean supportSafe,
        @Schema(description = "False: member/native setup must not receive raw calendar-provider configuration.",
                example = "false")
        boolean providerConfigurationExposed,
        @Schema(description = "False: this contract never returns provider credentials or bearer tokens.",
                example = "false")
        boolean credentialsExposed,
        @Schema(description = "Weave-owned calendar facade base path for native setup/status.", example = "/api/calendar")
        String facadeBasePath,
        @Schema(description = "Weave-owned setup credential lifecycle path.", example = "/api/calendar/client-setup/credentials")
        String credentialLifecyclePath,
        @Schema(description = "Weave-owned Apple profile download path. The profile itself remains unavailable until signing and scoped credentials exist.",
                example = "/api/calendar/client-setup/apple.mobileconfig")
        String appleProfilePath,
        @Schema(description = "Weave-owned event sync facade path template.", example = "/api/calendar/events?scopeType={scopeType}")
        String eventSyncPathTemplate,
        @Schema(description = "OS-specific sync setup options.")
        List<CalendarNativeSyncOptionResponse> options,
        @Schema(description = "Executable proof hooks that can be exercised before full native sync availability.")
        List<String> proofHooks,
        @Schema(description = "Support-safe blockers before native calendar availability can be true.")
        List<String> blockedUntil) {

    public CalendarNativeSyncSetupResponse {
        options = options == null ? List.of() : List.copyOf(options);
        proofHooks = proofHooks == null ? List.of() : List.copyOf(proofHooks);
        blockedUntil = blockedUntil == null ? List.of() : List.copyOf(blockedUntil);
    }
}
