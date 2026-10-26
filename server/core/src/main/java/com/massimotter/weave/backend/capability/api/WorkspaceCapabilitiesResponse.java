package com.massimotter.weave.backend.capability.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Schema(description = "Required Core policy and typed statuses of installed domains, keyed by stable domain identity. Presence means installation, independently of readiness and member authorization.")
public record WorkspaceCapabilitiesResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) WorkspaceCapabilityStatusResponse shellAccess,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) WorkspaceCapabilityStatusResponse adminControlPlane,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Map<String, WorkspaceCapabilityStatusResponse> domains) {
    public WorkspaceCapabilitiesResponse {
        Objects.requireNonNull(shellAccess, "shellAccess");
        Objects.requireNonNull(adminControlPlane, "adminControlPlane");
        Objects.requireNonNull(domains, "domains");
        domains.forEach((id, status) -> {
            if (id == null || !id.matches("[a-z][a-z0-9-]*")) {
                throw new IllegalArgumentException("A stable installed domain identity is required");
            }
            Objects.requireNonNull(status, "Installed domain status");
        });
        domains = Collections.unmodifiableMap(new LinkedHashMap<>(domains));
    }
}
