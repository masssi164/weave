package com.massimotter.weave.backend.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Context-authorized, support-safe recent activity shown on Weave Home.")
public record WorkspaceHomeRecentActivityResponse(
        @Schema(description = "Stable opaque activity reference; never a provider or resource identifier.")
        String activityRef,
        @Schema(description = "Canonical Weave domain.", allowableValues = {"files"})
        String domain,
        @Schema(description = "Canonical completed Weave mutation action.", example = "files.webdav_write.completed")
        String action,
        Instant occurredAt,
        @Schema(description = "Canonical visibility without a raw Context identifier.", allowableValues = {"workspace"})
        String visibility,
        @Schema(description = "Tenant-scoped opaque SHA-256 actor reference.")
        String actorRefHash,
        boolean actorIsCurrentUser,
        boolean supportSafe) {
}
