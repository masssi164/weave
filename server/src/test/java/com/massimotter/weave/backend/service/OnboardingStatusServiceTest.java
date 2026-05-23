package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.config.OnboardingStatusProperties;
import com.massimotter.weave.backend.config.PlatformContractProperties;
import com.massimotter.weave.backend.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.model.OnboardingProvisioningState;
import com.massimotter.weave.backend.model.OnboardingStatusResponse;
import com.massimotter.weave.backend.model.WorkspaceCapabilityReadiness;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;

class OnboardingStatusServiceTest {

    @Test
    void derivesNotConfiguredAndDegradedProvisioningStatesFromCapabilities() {
        OAuth2ResourceServerProperties resourceServerProperties = new OAuth2ResourceServerProperties();
        resourceServerProperties.getJwt().setIssuerUri("https://auth.weave.local/realms/weave");
        OnboardingStatusService service = new OnboardingStatusService(
                resourceServerProperties,
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new WorkspaceCapabilityProperties(
                        new WorkspaceCapabilityProperties.Capability(true, null, null),
                        new WorkspaceCapabilityProperties.Capability(true, "https://matrix.weave.local", WorkspaceCapabilityReadiness.DEGRADED),
                        new WorkspaceCapabilityProperties.Capability(false, null, null),
                        new WorkspaceCapabilityProperties.Capability(false, null, null),
                        new WorkspaceCapabilityProperties.Capability(false, null, null)),
                new PlatformContractProperties(null, null, null, null, null, null, null, null),
                new OnboardingStatusProperties(null, null));

        OnboardingStatusResponse response = service.status(jwt(List.of("member")));

        assertThat(response.moduleProvisioning().matrix().state()).isEqualTo(OnboardingProvisioningState.DEGRADED);
        assertThat(response.moduleProvisioning().nextcloud().state()).isEqualTo(OnboardingProvisioningState.NOT_CONFIGURED);
        assertThat(response.firstRunComplete()).isFalse();
    }

    @Test
    void honorsExplicitPendingAndFailedProvisioningOverrides() {
        OAuth2ResourceServerProperties resourceServerProperties = new OAuth2ResourceServerProperties();
        resourceServerProperties.getJwt().setIssuerUri("https://auth.weave.local/realms/weave");
        OnboardingStatusService service = new OnboardingStatusService(
                resourceServerProperties,
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new WorkspaceCapabilityProperties(
                        new WorkspaceCapabilityProperties.Capability(true, null, null),
                        new WorkspaceCapabilityProperties.Capability(true, "https://matrix.weave.local", null),
                        new WorkspaceCapabilityProperties.Capability(true, "https://files.weave.local", null),
                        new WorkspaceCapabilityProperties.Capability(false, null, null),
                        new WorkspaceCapabilityProperties.Capability(false, null, null)),
                new PlatformContractProperties(null, null, null, null, null, null, null, null),
                new OnboardingStatusProperties(
                        new OnboardingStatusProperties.ModuleProvisioning(OnboardingProvisioningState.PENDING),
                        new OnboardingStatusProperties.ModuleProvisioning(OnboardingProvisioningState.FAILED)));

        OnboardingStatusResponse response = service.status(jwt(List.of("member")));

        assertThat(response.moduleProvisioning().matrix().state()).isEqualTo(OnboardingProvisioningState.PENDING);
        assertThat(response.moduleProvisioning().nextcloud().state()).isEqualTo(OnboardingProvisioningState.FAILED);
        assertThat(response.moduleProvisioning().matrix().message()).doesNotContain("Exception", "stack trace");
        assertThat(response.moduleProvisioning().nextcloud().message()).doesNotContain("Exception", "stack trace");
        assertThat(response.firstRunComplete()).isFalse();
    }

    private Jwt jwt(List<String> roles) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-123")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .claim("preferred_username", "alice")
                .claim("name", "Alice Example")
                .claim("email", "alice@example.com")
                .claim("email_verified", true)
                .claim("aud", List.of("weave-app"))
                .claim("realm_access", Map.of("roles", roles))
                .build();
    }
}
