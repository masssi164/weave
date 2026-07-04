package com.massimotter.weave.backend.model.files;

import com.massimotter.weave.backend.model.WorkspaceCapabilityStatusResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Support-safe native Files provider setup contract for OS extensions/providers.")
public record FileNativeProviderSetupResponse(
        @Schema(description = "Files capability readiness as seen by the authenticated member.")
        WorkspaceCapabilityStatusResponse readiness,
        @Schema(description = "True when this setup contract excludes raw provider endpoints, credentials, and diagnostics.",
                example = "true")
        boolean supportSafe,
        @Schema(description = "False: member/native setup must not receive raw storage-provider configuration.",
                example = "false")
        boolean providerConfigurationExposed,
        @Schema(description = "False: this contract never returns provider credentials or bearer tokens.",
                example = "false")
        boolean credentialsExposed,
        @Schema(description = "Weave-owned facade base path for native providers.", example = "/api/files")
        String facadeBasePath,
        @Schema(description = "Weave-owned list path template for native providers.", example = "/api/files?path={path}")
        String listPathTemplate,
        @Schema(description = "Weave-owned download path template for native providers.", example = "/api/files/{id}/download")
        String downloadPathTemplate,
        @Schema(description = "Weave-owned upload path for native providers.", example = "/api/files/upload")
        String uploadPath,
        @Schema(description = "OS-specific provider setup options.")
        List<FileNativeProviderOptionResponse> options,
        @Schema(description = "Executable proof hooks that can be exercised before full OS extension availability.")
        List<String> proofHooks,
        @Schema(description = "Support-safe blockers before native provider availability can be true.")
        List<String> blockedUntil) {

    public FileNativeProviderSetupResponse {
        options = options == null ? List.of() : List.copyOf(options);
        proofHooks = proofHooks == null ? List.of() : List.copyOf(proofHooks);
        blockedUntil = blockedUntil == null ? List.of() : List.copyOf(blockedUntil);
    }
}
