package com.massimotter.weave.backend.model.spaces;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record SpaceMemberChangeRequest(
        @NotEmpty @Size(max = 3)
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Hierarchical current grant: VIEW, VIEW+EDIT, or VIEW+EDIT+ADMIN.")
        List<PermissionValue> permissions) {
    public SpaceMemberChangeRequest {
        permissions = permissions == null ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(permissions));
    }

    public enum PermissionValue { VIEW, EDIT, ADMIN }
}
