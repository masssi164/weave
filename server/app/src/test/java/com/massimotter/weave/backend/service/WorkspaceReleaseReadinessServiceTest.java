package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.testing.WorkspaceCapabilityTestInstallation;
import com.massimotter.weave.backend.capability.readiness.WorkspaceReleaseReadinessService;
import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import com.massimotter.weave.backend.support.HumanJwtTestSupport;

import com.massimotter.weave.backend.security.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.testing.WorkspaceCapabilityTestInstallation.Settings;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityReadiness;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceReleaseReadinessServiceTest {

    @Test
    void reportsConfigurationWithoutClaimingLiveProvidersOrReleaseReadiness() {
        WorkspaceCapabilityService capabilityService = WorkspaceCapabilityTestInstallation.service(
                resourceServerProperties("https://auth.weave.test/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new Settings(
                        new Settings.Capability(true, null, null),
                        new Settings.Capability(true, "https://matrix.weave.test", null),
                        new Settings.Capability(true, "https://files.weave.test", null),
                        null,
                        null,
                        null));

        WorkspaceReleaseReadinessService service = new WorkspaceReleaseReadinessService(resourceServerProperties("https://auth.weave.test/realms/weave"),
new WeaveSecurityProperties("weave-app", "weave-app"),
capabilityService);

        var snapshot = service.snapshot(jwt("admin"));

        assertThat(snapshot.readiness()).isEqualTo(WorkspaceCapabilityReadiness.READY);
        assertThat(snapshot.actions()).isEmpty();
        assertThat(snapshot.checks()).hasSize(3);
        assertThat(snapshot.summary()).contains("Live provider operation and release verification are separate");
        assertThat(snapshot.checks().get(1).message()).contains("does not verify live Matrix operations");
        assertThat(snapshot.checks().get(2).label()).isEqualTo("Files capability configuration");
        assertThat(snapshot.checks().get(2).message()).contains("Inspect current binding status");
        assertThat(snapshot.toString()).doesNotContain("Nextcloud", "reachable", "ready to ship");
    }

    @Test
    void returnsBlockedWhenAuthContractIsMissing() {
        Settings properties = new Settings(
                new Settings.Capability(true, null, null),
                new Settings.Capability(true, null, null),
                new Settings.Capability(true, null, null),
                null,
                null,
                null);
        WorkspaceCapabilityService capabilityService = WorkspaceCapabilityTestInstallation.service(
                resourceServerProperties(null),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                properties);
        WorkspaceReleaseReadinessService service = new WorkspaceReleaseReadinessService(resourceServerProperties(null),
new WeaveSecurityProperties("weave-app", "weave-app"),
capabilityService);

        var snapshot = service.snapshot(jwt("admin"));

        assertThat(snapshot.readiness()).isEqualTo(WorkspaceCapabilityReadiness.BLOCKED);
        assertThat(snapshot.actions()).contains("Provide the missing auth runtime inputs for the backend: WEAVE_OIDC_ISSUER_URI.");
        assertThat(snapshot.checks())
                .extracting(check -> check.key() + ":" + check.readiness().value())
                .contains("auth-contract:blocked", "chat:blocked", "files:blocked");
    }

    @Test
    void returnsDegradedWhenRoutesAreStillMissing() {
        Settings properties = new Settings(
                new Settings.Capability(true, null, null),
                new Settings.Capability(true, null, null),
                new Settings.Capability(true, null, null),
                null,
                null,
                null);
        WorkspaceCapabilityService capabilityService = WorkspaceCapabilityTestInstallation.service(
                resourceServerProperties("https://auth.weave.test/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                properties);
        WorkspaceReleaseReadinessService service = new WorkspaceReleaseReadinessService(resourceServerProperties("https://auth.weave.test/realms/weave"),
new WeaveSecurityProperties("weave-app", "weave-app"),
capabilityService);

        var snapshot = service.snapshot(jwt("admin"));

        assertThat(snapshot.readiness()).isEqualTo(WorkspaceCapabilityReadiness.DEGRADED);
        assertThat(snapshot.actions()).containsExactly(
                "Set WEAVE_MATRIX_BASE_URL to the southbound Matrix provider URL; clients receive the Weave facade from the API origin.",
                "Review Files capability configuration and current binding diagnostics at /api/admin/providers/status.");
    }

    private Jwt jwt(String role) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(role + "-123")
                .issuer("https://auth.example.invalid/realms/acme")
                .claim("organization", HumanJwtTestSupport.organizationWithRole(role))
                .build();
    }

    private OAuth2ResourceServerProperties resourceServerProperties(String issuerUri) {
        OAuth2ResourceServerProperties properties = new OAuth2ResourceServerProperties();
        properties.getJwt().setIssuerUri(issuerUri);
        return properties;
    }
}
