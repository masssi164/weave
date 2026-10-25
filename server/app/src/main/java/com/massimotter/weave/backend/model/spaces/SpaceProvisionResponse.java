package com.massimotter.weave.backend.model.spaces;

import io.swagger.v3.oas.annotations.media.Schema;

public record SpaceProvisionResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String spaceRef,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"CREATED", "UNCHANGED"}) String result) {
}
