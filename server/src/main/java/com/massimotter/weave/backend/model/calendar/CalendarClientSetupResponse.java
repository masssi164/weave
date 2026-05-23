package com.massimotter.weave.backend.model.calendar;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Secret-free calendar client setup metadata for native and desktop calendar apps.")
public record CalendarClientSetupResponse(
        @Schema(description = "Calendar ownership scope exposed by the Weave calendar facade.")
        CalendarScopeResponse scope,
        @Schema(description = "Explicit product/private/external-client access model.")
        CalendarAccessModelResponse accessModel,
        @Schema(description = "Safety readiness for profile generation and tokenized subscription paths.")
        CalendarCredentialReadinessResponse credentialReadiness,
        @Schema(description = "Authenticated user's external calendar account identifier.", example = "maria")
        String username,
        @Schema(description = "CalDAV/WebDAV discovery URLs that may be shown to the user without credentials.")
        CalendarExternalEndpointsResponse endpoints,
        @Schema(description = "Explicit credential policy for external clients.")
        String credentialPolicy,
        @Schema(description = "Platform-specific setup options. Options never contain passwords or bearer tokens.")
        List<CalendarClientSetupOptionResponse> options) {
}
