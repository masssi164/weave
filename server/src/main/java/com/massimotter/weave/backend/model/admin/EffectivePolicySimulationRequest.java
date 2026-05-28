package com.massimotter.weave.backend.model.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Admin-only support-safe effective capability policy simulation request.")
public record EffectivePolicySimulationRequest(
        String subject,
        String organizationId,
        List<String> roles,
        List<String> groups,
        List<String> requestedCapabilities,
        String reason) {

    public EffectivePolicySimulationRequest {
        roles = roles == null ? List.of() : List.copyOf(roles);
        groups = groups == null ? List.of() : List.copyOf(groups);
        requestedCapabilities = requestedCapabilities == null ? List.of() : List.copyOf(requestedCapabilities);
    }
}
