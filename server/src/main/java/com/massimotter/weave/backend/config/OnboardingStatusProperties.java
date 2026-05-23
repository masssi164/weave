package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.model.OnboardingProvisioningState;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "weave.onboarding")
public record OnboardingStatusProperties(
        ModuleProvisioning matrix,
        ModuleProvisioning nextcloud) {

    public OnboardingStatusProperties {
        matrix = module(matrix);
        nextcloud = module(nextcloud);
    }

    private static ModuleProvisioning module(ModuleProvisioning module) {
        return module == null ? new ModuleProvisioning(null) : module;
    }

    public record ModuleProvisioning(OnboardingProvisioningState provisioningState) {
    }
}
