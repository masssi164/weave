package com.massimotter.weave.backend.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Support-safe credential lifecycle summary for member, native, generic, and MCP clients.")
public record ClientAccessCredentialLifecycleResponse(
        @Schema(description = "Stable lifecycle posture.", example = "blocked_until_revocable_device_grants")
        String status,
        @Schema(description = "Whether credentials or bearer tokens are returned in this manifest. Must remain false.",
                example = "false")
        boolean secretMaterialReturned,
        @Schema(description = "Weave-owned lifecycle paths or references only; never provider URLs or raw secret refs.")
        List<String> lifecyclePaths,
        @Schema(description = "Support-safe blockers before this access surface can be called ready.")
        List<String> blockedUntil) {

    public ClientAccessCredentialLifecycleResponse {
        lifecyclePaths = lifecyclePaths == null ? List.of() : List.copyOf(lifecyclePaths);
        blockedUntil = blockedUntil == null ? List.of() : List.copyOf(blockedUntil);
    }
}
