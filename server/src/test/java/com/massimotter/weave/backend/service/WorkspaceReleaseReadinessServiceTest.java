package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.model.WorkspaceCapabilityReadiness;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceReleaseReadinessServiceTest {

    @Test
    void returnsReadyWhenAuthChatAndFilesAreConfigured() {
        WorkspaceCapabilityService capabilityService = new WorkspaceCapabilityService(
                resourceServerProperties("https://auth.weave.local/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new WorkspaceCapabilityProperties(
                        new WorkspaceCapabilityProperties.Capability(true, null, null),
                        new WorkspaceCapabilityProperties.Capability(true, "https://matrix.weave.local", null),
                        new WorkspaceCapabilityProperties.Capability(true, "https://files.weave.local", null),
                        null,
                        null));

        WorkspaceReleaseReadinessService service = new WorkspaceReleaseReadinessService(
                resourceServerProperties("https://auth.weave.local/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new WorkspaceCapabilityProperties(
                        new WorkspaceCapabilityProperties.Capability(true, null, null),
                        new WorkspaceCapabilityProperties.Capability(true, "https://matrix.weave.local", null),
                        new WorkspaceCapabilityProperties.Capability(true, "https://files.weave.local", null),
                        null,
                        null),
                capabilityService);

        var snapshot = service.snapshot();

        assertThat(snapshot.readiness()).isEqualTo(WorkspaceCapabilityReadiness.READY);
        assertThat(snapshot.actions()).isEmpty();
        assertThat(snapshot.checks()).hasSize(3);
    }

    @Test
    void returnsBlockedWhenAuthContractIsMissing() {
        WorkspaceCapabilityProperties properties = new WorkspaceCapabilityProperties(
                new WorkspaceCapabilityProperties.Capability(true, null, null),
                new WorkspaceCapabilityProperties.Capability(true, null, null),
                new WorkspaceCapabilityProperties.Capability(true, null, null),
                null,
                null);
        WorkspaceCapabilityService capabilityService = new WorkspaceCapabilityService(
                resourceServerProperties(null),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                properties);
        WorkspaceReleaseReadinessService service = new WorkspaceReleaseReadinessService(
                resourceServerProperties(null),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                properties,
                capabilityService);

        var snapshot = service.snapshot();

        assertThat(snapshot.readiness()).isEqualTo(WorkspaceCapabilityReadiness.BLOCKED);
        assertThat(snapshot.actions()).contains("Provide the missing auth runtime inputs for the backend: WEAVE_OIDC_ISSUER_URI.");
        assertThat(snapshot.checks())
                .extracting(check -> check.key() + ":" + check.readiness().value())
                .contains("auth-contract:blocked", "chat:blocked", "files:blocked");
    }

    @Test
    void returnsDegradedWhenRoutesAreStillMissing() {
        WorkspaceCapabilityProperties properties = new WorkspaceCapabilityProperties(
                new WorkspaceCapabilityProperties.Capability(true, null, null),
                new WorkspaceCapabilityProperties.Capability(true, null, null),
                new WorkspaceCapabilityProperties.Capability(true, null, null),
                null,
                null);
        WorkspaceCapabilityService capabilityService = new WorkspaceCapabilityService(
                resourceServerProperties("https://auth.weave.local/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                properties);
        WorkspaceReleaseReadinessService service = new WorkspaceReleaseReadinessService(
                resourceServerProperties("https://auth.weave.local/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                properties,
                capabilityService);

        var snapshot = service.snapshot();

        assertThat(snapshot.readiness()).isEqualTo(WorkspaceCapabilityReadiness.DEGRADED);
        assertThat(snapshot.actions()).containsExactly(
                "Set WEAVE_MATRIX_HOMESERVER_URL to the public Matrix base URL, for example https://matrix.weave.local.",
                "Set WEAVE_NEXTCLOUD_BASE_URL to the canonical Nextcloud URL, for example https://files.weave.local.");
    }

    private OAuth2ResourceServerProperties resourceServerProperties(String issuerUri) {
        OAuth2ResourceServerProperties properties = new OAuth2ResourceServerProperties();
        properties.getJwt().setIssuerUri(issuerUri);
        return properties;
    }
}
