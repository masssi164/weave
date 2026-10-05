package com.massimotter.weave.backend.model.admin;

import io.swagger.v3.oas.annotations.media.Schema;

/** Current Files authority metadata; configuration handles and object mappings stay private. */
public record FilesBindingStatusResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BindingState bindingState,
        @Schema(nullable = true, minimum = "1") Long bindingRevision,
        @Schema(nullable = true) String adapterKey,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Readiness readiness) {
    public enum BindingState { ACTIVE, NO_ACTIVE_BINDING }
    public enum Readiness { CONFIGURED, NOT_CONFIGURED, UNAVAILABLE }
}
