package com.massimotter.weave.backend.model.spaces;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record SpaceRelationshipListResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<SpaceRelationshipResponse> relationships,
        @Schema(description = "Opaque cursor for the next bounded direct-relationship page.")
        String nextAfterRelationRef) {
    public SpaceRelationshipListResponse {
        relationships = List.copyOf(relationships);
    }
}
