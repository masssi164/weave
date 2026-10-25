package com.massimotter.weave.backend.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Current profile synchronization status for backing modules.")
public record ModuleSyncStatusResponse(
        @Schema(description = "Matrix profile sync status.", example = "not_configured")
        String matrix,
        @Schema(description = "Nextcloud profile sync status.", example = "not_configured")
        String nextcloud) {
}
