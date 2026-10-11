package com.massimotter.weave.backend.model.spaces;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record SpaceMemberListResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<SpaceMemberResponse> members,
        @Schema(description = "Last returned account reference when another page exists.")
        String nextCursor) {
    public SpaceMemberListResponse {
        members = List.copyOf(members);
    }
}
