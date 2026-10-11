package com.massimotter.weave.backend.model.spaces;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record SpaceUserListResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<SpaceUserResponse> spaces,
        @Schema(description = "Pass this last seen Space reference as afterSpaceRef to request the next page.")
        String nextAfterSpaceRef) {
    public SpaceUserListResponse {
        spaces = List.copyOf(spaces);
    }
}
