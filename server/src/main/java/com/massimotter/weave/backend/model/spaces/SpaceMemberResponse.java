package com.massimotter.weave.backend.model.spaces;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record SpaceMemberResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String accountRef,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<SpaceMemberChangeRequest.PermissionValue> permissions) {
    public SpaceMemberResponse {
        permissions = List.copyOf(permissions);
    }
}
