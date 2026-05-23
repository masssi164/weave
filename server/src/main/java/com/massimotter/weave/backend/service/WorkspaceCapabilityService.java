package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.model.WorkspaceCapabilitiesResponse;
import com.massimotter.weave.backend.model.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.model.WorkspaceCapabilityStatusResponse;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceCapabilityService {

    private final OAuth2ResourceServerProperties resourceServerProperties;
    private final WeaveSecurityProperties weaveSecurityProperties;
    private final WorkspaceCapabilityProperties workspaceCapabilityProperties;

    public WorkspaceCapabilityService(
            OAuth2ResourceServerProperties resourceServerProperties,
            WeaveSecurityProperties weaveSecurityProperties,
            WorkspaceCapabilityProperties workspaceCapabilityProperties) {
        this.resourceServerProperties = resourceServerProperties;
        this.weaveSecurityProperties = weaveSecurityProperties;
        this.workspaceCapabilityProperties = workspaceCapabilityProperties;
    }

    public WorkspaceCapabilitiesResponse snapshot() {
        WorkspaceCapabilityReadiness shellAccessReadiness = shellAccessReadiness();
        return new WorkspaceCapabilitiesResponse(
                status(workspaceCapabilityProperties.shellAccess(), shellAccessReadiness),
                dependentStatus(workspaceCapabilityProperties.chat(), shellAccessReadiness),
                dependentStatus(workspaceCapabilityProperties.files(), shellAccessReadiness),
                standaloneStatus(workspaceCapabilityProperties.calendar(), WorkspaceCapabilityReadiness.UNAVAILABLE),
                standaloneStatus(workspaceCapabilityProperties.boards(), WorkspaceCapabilityReadiness.UNAVAILABLE));
    }

    private WorkspaceCapabilityStatusResponse dependentStatus(
            WorkspaceCapabilityProperties.Capability capability,
            WorkspaceCapabilityReadiness shellAccessReadiness) {
        if (!capability.enabled()) {
            return status(capability, WorkspaceCapabilityReadiness.UNAVAILABLE);
        }
        if (shellAccessReadiness == WorkspaceCapabilityReadiness.BLOCKED) {
            return status(capability, WorkspaceCapabilityReadiness.BLOCKED);
        }
        if (capability.readiness() != null) {
            return status(capability, capability.readiness());
        }
        if (hasText(capability.dependencyUrl())) {
            return status(capability, WorkspaceCapabilityReadiness.READY);
        }
        return status(capability, WorkspaceCapabilityReadiness.DEGRADED);
    }

    private WorkspaceCapabilityStatusResponse standaloneStatus(
            WorkspaceCapabilityProperties.Capability capability,
            WorkspaceCapabilityReadiness defaultReadiness) {
        if (!capability.enabled()) {
            return status(capability, WorkspaceCapabilityReadiness.UNAVAILABLE);
        }
        if (capability.readiness() != null) {
            return status(capability, capability.readiness());
        }
        return status(capability, defaultReadiness);
    }

    private WorkspaceCapabilityReadiness shellAccessReadiness() {
        if (!workspaceCapabilityProperties.shellAccess().enabled()) {
            return WorkspaceCapabilityReadiness.UNAVAILABLE;
        }
        boolean hasIssuer = hasText(resourceServerProperties.getJwt().getIssuerUri());
        boolean hasAudience = hasText(weaveSecurityProperties.requiredAudience());
        boolean hasClientId = hasText(weaveSecurityProperties.clientId());
        return hasIssuer && hasAudience && hasClientId
                ? WorkspaceCapabilityReadiness.READY
                : WorkspaceCapabilityReadiness.BLOCKED;
    }

    private WorkspaceCapabilityStatusResponse status(
            WorkspaceCapabilityProperties.Capability capability,
            WorkspaceCapabilityReadiness readiness) {
        return new WorkspaceCapabilityStatusResponse(capability.enabled(), readiness);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
