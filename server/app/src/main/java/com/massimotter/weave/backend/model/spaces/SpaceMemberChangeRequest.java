package com.massimotter.weave.backend.model.spaces;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record SpaceMemberChangeRequest(
        @NotNull
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Hierarchical current grant: VIEW, VIEW+EDIT, or VIEW+EDIT+ADMIN.")
        PermissionLevel permissionLevel) {

    public enum PermissionLevel { VIEW, EDIT, ADMIN }
    public enum PermissionValue { VIEW, EDIT, ADMIN }
}
