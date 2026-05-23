package com.massimotter.weave.backend.model.calendar;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Secret-free standards endpoints for external calendar clients.")
public record CalendarExternalEndpointsResponse(
        @Schema(description = "Canonical Nextcloud/CalDAV technical base URL.", example = "https://files.weave.local")
        String serverUrl,
        @Schema(description = "CalDAV discovery root for DAV clients.", example = "https://files.weave.local/remote.php/dav")
        String caldavDiscoveryUrl,
        @Schema(description = "User principal URL template for CalDAV clients. Contains no credential.",
                example = "https://files.weave.local/remote.php/dav/principals/users/maria/")
        String principalUrl) {
}
