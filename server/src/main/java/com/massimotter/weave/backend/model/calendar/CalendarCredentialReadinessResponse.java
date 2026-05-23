package com.massimotter.weave.backend.model.calendar;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Safety readiness for downloadable profiles, external credentials, and read-only feeds.")
public record CalendarCredentialReadinessResponse(
        @Schema(description = "Stable machine-readable readiness status.", example = "blocked_until_revocable_credentials")
        String status,
        @Schema(description = "Whether a signed Apple .mobileconfig download is available.", example = "false")
        boolean appleProfileSigned,
        @Schema(description = "Whether generated Apple profiles include a password or token.", example = "false")
        boolean appleProfilePasswordIncluded,
        @Schema(description = "Whether Weave can issue/revoke per-client CalDAV credentials.", example = "false")
        boolean revocableCredentialsAvailable,
        @Schema(description = "Whether read-only ICS/webcal feed tokens are available.", example = "false")
        boolean readOnlySubscriptionTokensAvailable,
        @Schema(description = "Whether backend actor credentials can appear in client-facing setup artifacts. Must remain false.", example = "false")
        boolean backendActorCredentialsExposed,
        @Schema(description = "Support-safe blockers before setup artifacts may become downloadable.")
        List<String> blockers) {
}
