package com.massimotter.weave.backend.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "One Weave-owned client access projection for a domain facade.")
public record ClientAccessProtocolSurfaceResponse(
        @Schema(description = "Surface kind, such as openapi, standard-protocol, native-os, mcp, or provider-adapter.")
        String kind,
        @Schema(description = "Surface name in product-neutral terms.", example = "Weave WebDAV projection")
        String name,
        @Schema(description = "Weave-owned API, setup, status, or lifecycle path when one exists. Null means the surface is documented but not exposed yet.")
        String setupPath,
        @Schema(description = "Stable readiness posture for this surface.", example = "contract_ready")
        String readiness,
        @Schema(description = "Support-safe notes for generated clients and admins.")
        List<String> notes) {

    public ClientAccessProtocolSurfaceResponse {
        notes = notes == null ? List.of() : List.copyOf(notes);
    }
}
