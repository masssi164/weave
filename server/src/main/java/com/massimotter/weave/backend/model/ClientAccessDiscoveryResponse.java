package com.massimotter.weave.backend.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Support-safe discovery metadata for a Weave domain access surface.")
public record ClientAccessDiscoveryResponse(
        @Schema(description = "Provider-neutral Weave domain key.", example = "files")
        String domain,
        @Schema(description = "Weave-owned product API base path.", example = "/api/files")
        String productApiBasePath,
        @Schema(description = "OpenAPI tag or contract group for generated clients and MCP allowlists.",
                example = "Files")
        String openApiTag,
        @Schema(description = "Open-standard or native projection surfaces exposed over Weave domain truth.")
        List<ClientAccessProtocolSurfaceResponse> surfaces,
        @Schema(description = "Credential and grant lifecycle posture for this domain.")
        ClientAccessCredentialLifecycleResponse credentialLifecycle,
        @Schema(description = "True when discovery excludes raw provider URLs, credentials, endpoint rotation data, and diagnostics.")
        boolean supportSafe,
        @Schema(description = "False: discovery must not expose raw provider setup or endpoint configuration.",
                example = "false")
        boolean providerConfigurationExposed) {

    public ClientAccessDiscoveryResponse {
        surfaces = surfaces == null ? List.of() : List.copyOf(surfaces);
    }
}
