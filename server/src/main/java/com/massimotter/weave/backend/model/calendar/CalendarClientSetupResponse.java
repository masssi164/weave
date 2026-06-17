package com.massimotter.weave.backend.model.calendar;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Secret-free calendar connection setup status for the Weave calendar facade.")
public record CalendarClientSetupResponse(
        @Schema(description = "Calendar ownership scope exposed by the Weave calendar facade.")
        CalendarScopeResponse scope,
        @Schema(description = "Canonical product access model for calendar connection setup.")
        CalendarAccessModelResponse accessModel,
        @Schema(description = "Readiness for downloadable profiles and subscription paths.")
        CalendarCredentialReadinessResponse credentialReadiness,
        @Schema(description = "Opaque authenticated calendar account reference for support correlation. Contains no credential or provider identifier.", example = "calendar-account:user-123")
        String accountReference,
        @Schema(description = "Credential safety policy for user-controlled setup flows.")
        String credentialPolicy,
        @Schema(description = "Platform-specific setup options. Options never contain passwords, bearer tokens, or provider discovery URLs.")
        List<CalendarClientSetupOptionResponse> options) {
}
