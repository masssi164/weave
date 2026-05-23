package com.massimotter.weave.backend.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Public platform configuration consumed by Weave clients.")
public record PlatformConfigResponse(
        String publicBaseUrl,
        String apiBaseUrl,
        String authBaseUrl,
        String matrixHomeserverUrl,
        String filesProductUrl,
        String calendarProductUrl,
        String nextcloudBaseUrl,
        Targets targets,
        Features features) {

    public record Targets(boolean mobile, boolean desktop, boolean web) {
    }

    public record Features(
            boolean chat,
            boolean chatE2ee,
            boolean matrixFederation,
            boolean files,
            boolean calendar) {
    }
}
