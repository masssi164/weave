package com.massimotter.weave.backend.capability.port;

import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityStatusResponse;
import com.massimotter.weave.backend.capability.api.WorkspaceReleaseReadinessCheckResponse;
import com.massimotter.weave.backend.capability.readiness.CapabilityAvailability;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** Installed capability declarations plus the owning domain's readiness projection. */
public record WorkspaceCapabilityContribution(
        WorkspaceCapabilityDefinition definition,
        Function<WorkspaceCapabilityReadiness, CapabilityAvailability> readiness,
        Function<WorkspaceCapabilityStatusResponse, Optional<WorkspaceReleaseReadinessCheckResponse>> releaseCheck) {
    public WorkspaceCapabilityContribution(WorkspaceCapabilityDefinition definition,
            Function<WorkspaceCapabilityReadiness, CapabilityAvailability> readiness) {
        this(definition, readiness, ignored -> Optional.empty());
    }

    public WorkspaceCapabilityContribution {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(readiness, "readiness");
        Objects.requireNonNull(releaseCheck, "releaseCheck");
    }
}
