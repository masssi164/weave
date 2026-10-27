package com.massimotter.weave.backend.spaces.api;

import io.swagger.v3.oas.annotations.media.Schema;

/** Opaque Weave-owned Space reference; provider identifiers never appear here. */
public record SpaceUserResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Stable Weave Space reference.")
        String spaceRef,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Current readable Space name.")
        String displayName) {
}
