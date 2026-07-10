package com.massimotter.weave.backend.model;

import io.swagger.v3.oas.annotations.media.Schema;

public record WeaverPermissionModeRequest(
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"deny", "allowlist", "ask", "auto", "full"})
        String mode) {
}
