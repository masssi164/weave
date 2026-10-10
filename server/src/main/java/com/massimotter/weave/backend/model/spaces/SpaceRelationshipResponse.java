package com.massimotter.weave.backend.model.spaces;

import io.swagger.v3.oas.annotations.media.Schema;

/** Direct, explicitly materialized Weave resource relationship. */
public record SpaceRelationshipResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String relationRef,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = "CONTAINS") String relationKind,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"FILE", "EVENT", "ROOM"})
        String targetKind,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String targetRef) {
}
