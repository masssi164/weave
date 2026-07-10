package com.massimotter.weave.backend.model;

import io.swagger.v3.oas.annotations.media.Schema;

public record WeaverPermissionModeResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean accepted,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String mode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean dangerous,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String policyReason,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String runtimeProfileHash) {
}
