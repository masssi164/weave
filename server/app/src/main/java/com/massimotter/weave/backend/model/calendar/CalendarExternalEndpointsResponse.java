package com.massimotter.weave.backend.model.calendar;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Secret-free Weave CalDAV projection paths for external calendar clients.")
public record CalendarExternalEndpointsResponse(
        @Schema(description = "Weave-owned CalDAV projection base path.", example = "/caldav")
        String serverUrl,
        @Schema(description = "Weave-owned CalDAV discovery root for DAV clients.", example = "/caldav")
        String caldavDiscoveryUrl,
        @Schema(description = "Weave-owned user principal path template for CalDAV clients. Contains no credential.",
                example = "/caldav/principals/users/maria/")
        String principalUrl) {
}
