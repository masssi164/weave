package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.model.WorkspaceCapabilityReadiness;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceCapabilityServiceTest {

    @Test
    void marksChatAndFilesDegradedUntilTheirRoutesAreConfigured() {
        WorkspaceCapabilityService service = new WorkspaceCapabilityService(
                resourceServerProperties("https://auth.weave.local/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new WorkspaceCapabilityProperties(null, null, null, null, null));

        var snapshot = service.snapshot();

        assertThat(snapshot.shellAccess().readiness()).isEqualTo(WorkspaceCapabilityReadiness.READY);
        assertThat(snapshot.chat().enabled()).isTrue();
        assertThat(snapshot.chat().readiness()).isEqualTo(WorkspaceCapabilityReadiness.DEGRADED);
        assertThat(snapshot.files().readiness()).isEqualTo(WorkspaceCapabilityReadiness.DEGRADED);
        assertThat(snapshot.calendar().readiness()).isEqualTo(WorkspaceCapabilityReadiness.UNAVAILABLE);
    }

    @Test
    void blocksDependentCapabilitiesWhenShellAccessCannotValidateTokens() {
        WorkspaceCapabilityService service = new WorkspaceCapabilityService(
                resourceServerProperties(null),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new WorkspaceCapabilityProperties(
                        new WorkspaceCapabilityProperties.Capability(true, null, null),
                        new WorkspaceCapabilityProperties.Capability(true, "https://matrix.weave.local", null),
                        new WorkspaceCapabilityProperties.Capability(true, "https://files.weave.local", null),
                        null,
                        null));

        var snapshot = service.snapshot();

        assertThat(snapshot.shellAccess().readiness()).isEqualTo(WorkspaceCapabilityReadiness.BLOCKED);
        assertThat(snapshot.chat().readiness()).isEqualTo(WorkspaceCapabilityReadiness.BLOCKED);
        assertThat(snapshot.files().readiness()).isEqualTo(WorkspaceCapabilityReadiness.BLOCKED);
    }

    @Test
    void usesConfiguredReadinessOverridesForEnabledCapabilities() {
        WorkspaceCapabilityService service = new WorkspaceCapabilityService(
                resourceServerProperties("https://auth.weave.local/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new WorkspaceCapabilityProperties(
                        new WorkspaceCapabilityProperties.Capability(true, null, null),
                        new WorkspaceCapabilityProperties.Capability(true, "https://matrix.weave.local", WorkspaceCapabilityReadiness.DEGRADED),
                        new WorkspaceCapabilityProperties.Capability(true, "https://files.weave.local", WorkspaceCapabilityReadiness.BLOCKED),
                        new WorkspaceCapabilityProperties.Capability(true, null, WorkspaceCapabilityReadiness.READY),
                        new WorkspaceCapabilityProperties.Capability(false, null, WorkspaceCapabilityReadiness.READY)));

        var snapshot = service.snapshot();

        assertThat(snapshot.chat().readiness()).isEqualTo(WorkspaceCapabilityReadiness.DEGRADED);
        assertThat(snapshot.files().readiness()).isEqualTo(WorkspaceCapabilityReadiness.BLOCKED);
        assertThat(snapshot.calendar().readiness()).isEqualTo(WorkspaceCapabilityReadiness.READY);
        assertThat(snapshot.boards().readiness()).isEqualTo(WorkspaceCapabilityReadiness.UNAVAILABLE);
    }

    @Test
    void marksShellAccessUnavailableWhenTheCapabilityIsDisabled() {
        WorkspaceCapabilityService service = new WorkspaceCapabilityService(
                resourceServerProperties("https://auth.weave.local/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new WorkspaceCapabilityProperties(
                        new WorkspaceCapabilityProperties.Capability(false, null, null),
                        new WorkspaceCapabilityProperties.Capability(true, "https://matrix.weave.local", null),
                        new WorkspaceCapabilityProperties.Capability(true, "https://files.weave.local", null),
                        null,
                        null));

        var snapshot = service.snapshot();

        assertThat(snapshot.shellAccess().enabled()).isFalse();
        assertThat(snapshot.shellAccess().readiness()).isEqualTo(WorkspaceCapabilityReadiness.UNAVAILABLE);
        assertThat(snapshot.chat().readiness()).isEqualTo(WorkspaceCapabilityReadiness.READY);
        assertThat(snapshot.files().readiness()).isEqualTo(WorkspaceCapabilityReadiness.READY);
    }

    private OAuth2ResourceServerProperties resourceServerProperties(String issuerUri) {
        OAuth2ResourceServerProperties properties = new OAuth2ResourceServerProperties();
        properties.getJwt().setIssuerUri(issuerUri);
        return properties;
    }
}
