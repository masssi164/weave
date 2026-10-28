package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.testing.WorkspaceCapabilityTestInstallation;
import com.massimotter.weave.backend.discovery.application.PlatformContractService;
import com.massimotter.weave.backend.api.error.ApiErrorException;
import com.massimotter.weave.backend.chat.config.MatrixChatProperties;
import com.massimotter.weave.backend.discovery.config.PlatformContractProperties;
import com.massimotter.weave.backend.security.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.testing.WorkspaceCapabilityTestInstallation.Settings;
import org.junit.jupiter.api.Test;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformContractServiceTest {

    @Test
    void rejectsInvalidPublicOriginPathsAndPortsWithoutPublishingTheirValues() {
        for (String origin : java.util.List.of("https://weave.test/other", "https://weave.test:0",
                "https://weave.test:65536", "https://user:secret@weave.test")) {
            PlatformContractService invalid = service(new MatrixChatProperties(false, null, null), true,
                    new PlatformContractProperties(origin, null, null, null, null, null, null, null));
            assertThatThrownBy(invalid::config)
                    .isInstanceOf(com.massimotter.weave.backend.api.error.ApiErrorException.class)
                    .hasMessageContaining("configuration is unavailable")
                    .hasMessageNotContaining(origin);
        }
        for (String origin : java.util.List.of("https://weave.test", "https://weave.test:8443/")) {
            assertThat(service(new MatrixChatProperties(false, null, null), true,
                    new PlatformContractProperties(origin, null, null, null, null, null, null, null))
                    .config().organizationOrigin()).isEqualTo(origin);
        }
    }

    @Test
    void reportsE2eeEnabledOnlyWhenAllValidationGatesPassAndChatIsEnabled() {
        PlatformContractService service = service(
                new MatrixChatProperties(
                        false,
                        new MatrixChatProperties.E2ee(true, true, true, true, true, true, "matrix-smoke-e2e"),
                        new MatrixChatProperties.BackendBoundary("blocked", "fail_closed")),
                true);

        var config = service.config();
        var status = service.status("e2ee-ready-test");

        assertThat(config.protocols().matrixClientServerBaseUrl()).isEqualTo("https://api.weave.test");
        assertThat(status.matrix().e2eeEnabled()).isTrue();
        assertThat(status.matrix().e2ee().status()).isEqualTo("validated");
        assertThat(status.matrix().e2ee().source()).isEqualTo("matrix-smoke-e2e");
        assertThat(status.matrix().backendBoundary().serverReadableMessageContent()).isFalse();
        assertThat(status.matrix().backendBoundary().agentParticipation()).isEqualTo("blocked");
        assertThat(status.matrix().backendBoundary().connectorWritePolicy()).isEqualTo("fail_closed");
    }

    @Test
    void keepsE2eeUnavailableWhenOneValidationGateIsMissing() {
        PlatformContractService service = service(
                new MatrixChatProperties(
                        false,
                        new MatrixChatProperties.E2ee(true, true, true, true, true, false, "matrix-smoke-e2e"),
                        null),
                true);

        var status = service.status("e2ee-missing-gate-test");

        assertThat(status.matrix().e2eeEnabled()).isFalse();
        assertThat(status.matrix().e2ee().status()).isEqualTo("not_validated");
        assertThat(status.matrix().e2ee().accessibilityReviewed()).isFalse();
        assertThat(status.matrix().e2ee().action()).contains("Do not claim Matrix chat E2EE complete");
    }

    @Test
    void keepsE2eeUnavailableWhenChatIsDisabledEvenIfValidationFlagsAreTrue() {
        PlatformContractService service = service(
                new MatrixChatProperties(
                        false,
                        new MatrixChatProperties.E2ee(true, true, true, true, true, true, "matrix-smoke-e2e"),
                        null),
                false);

        var config = service.config();
        var status = service.status("chat-disabled-e2ee-test");

        assertThat(config.domains())
                .filteredOn(domain -> domain.domain().equals("chat"))
                .singleElement()
                .extracting(domain -> domain.state())
                .isEqualTo("not_configured");
        assertThat(status.matrix().readiness()).isEqualTo("unavailable");
        assertThat(status.matrix().e2eeEnabled()).isFalse();
        assertThat(status.matrix().e2ee().status()).isEqualTo("validated");
    }

    @Test
    void exposesOnlyNativeMatrixProtocolWithoutPublicDavUrls() {
        PlatformContractService service = service(
                new MatrixChatProperties(false, null, null),
                true,
                new PlatformContractProperties(
                        "https://weave.test",
                        "https://api.weave.test/api",
                        "https://auth.weave.test",
                        "https://api.weave.test",
                        "https://weave.test/files",
                        "https://weave.test/calendar",
                        "https://files.weave.test",
                        null));

        var config = service.config();
        var status = service.status("nextcloud-route-test");

        assertThat(config.schemaVersion()).isEqualTo(2);
        assertThat(config.userApiBaseUrl()).isEqualTo("https://api.weave.test/api");
        assertThat(config.protocols().matrixClientServerBaseUrl()).isEqualTo("https://api.weave.test");
        assertThat(status.nextcloud().readiness()).isEqualTo("ready");
        assertThat(status.nextcloud().message()).contains("Nextcloud");
    }

    @Test
    void derivesMatrixFacadeFromUserApiOriginAndBlocksAProviderUrl() {
        PlatformContractService configured = service(
                new MatrixChatProperties(false, null, null),
                true,
                new PlatformContractProperties(
                        null,
                        "https://api.weave.test/api/",
                        null,
                        "https://chat.weave.test",
                        null,
                        null,
                        null,
                        null));
        PlatformContractService derived = service(
                new MatrixChatProperties(false, null, null),
                true,
                new PlatformContractProperties(
                        null,
                        "https://api.weave.test/api/",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null));

        assertThat(configured.config().protocols().matrixClientServerBaseUrl())
                .isEqualTo("https://api.weave.test");
        assertThat(configured.status("wrong-matrix-url").matrix().readiness()).isEqualTo("blocked");
        assertThat(derived.config().protocols().matrixClientServerBaseUrl())
                .isEqualTo("https://api.weave.test");
        assertThat(derived.status("derived-matrix-url").matrix().readiness()).isEqualTo("ready");
    }

    @Test
    void advertisesASeparatelyRoutedWeaveFacadeWhenItsRouteAndServerNameAgree() {
        PlatformContractProperties platform = new PlatformContractProperties(
                null, "https://api.weave.test/api", null, "https://chat.weave.test",
                null, null, null, null);
        PlatformContractService routed = service(
                new MatrixChatProperties(false, null, null), true, platform,
                "https://chat.weave.test/", "chat.weave.test");

        assertThat(routed.config().protocols().matrixClientServerBaseUrl())
                .isEqualTo("https://chat.weave.test");
        assertThat(routed.status("separate-facade").matrix().readiness()).isEqualTo("ready");
    }

    @Test
    void neverProjectsCredentialsOrAPathFromTheRoutedFacade() {
        PlatformContractProperties platform = new PlatformContractProperties(
                null, null, null, null, null, null, null, null);
        PlatformContractService invalid = service(
                new MatrixChatProperties(false, null, null), true, platform,
                "https://user:secret@api.weave.test/_matrix/client", "api.weave.test");

        assertThatThrownBy(invalid::config)
                .isInstanceOf(com.massimotter.weave.backend.api.error.ApiErrorException.class)
                .hasMessageContaining("not configured safely");
    }

    @Test
    void blocksChatReadinessWhenAdvertisedAndRoutedFacadeOrServerNameDiverge() {
        PlatformContractProperties platform = new PlatformContractProperties(
                null, "https://api.weave.test:44443/api", null, null, null, null, null, null);

        PlatformContractService wrongRoute = service(
                new MatrixChatProperties(false, null, null), true, platform,
                "https://matrix.weave.test:44443", "api.weave.test");
        PlatformContractService wrongIdentity = service(
                new MatrixChatProperties(false, null, null), true, platform,
                "https://api.weave.test:44443", "matrix.weave.test");
        PlatformContractService aligned = service(
                new MatrixChatProperties(false, null, null), true, platform,
                "https://api.weave.test:44443", "api.weave.test");

        assertThat(wrongRoute.status("wrong-route").matrix().readiness()).isEqualTo("blocked");
        assertThat(wrongIdentity.status("wrong-identity").matrix().readiness()).isEqualTo("blocked");
        assertThat(aligned.status("aligned").matrix().readiness()).isEqualTo("ready");
    }

    @Test
    void rejectsCredentialsOrPathsInAdvertisedMatrixOrigin() {
        assertThatThrownBy(() -> new PlatformContractProperties(
                null, "https://member:secret@api.weave.test/api", null, null,
                null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        for (String advertised : java.util.List.of(
                "https://member:secret@api.weave.test", "https://api.weave.test/_matrix/client")) {
            var invalid = service(new MatrixChatProperties(false, null, null), true,
                    new PlatformContractProperties(null, null, null, advertised, null, null, null, null));
            assertThatThrownBy(invalid::config).isInstanceOf(ApiErrorException.class);
            assertThatThrownBy(() -> invalid.status("invalid-advertised-origin")).isInstanceOf(ApiErrorException.class);
        }
    }

    private PlatformContractService service(MatrixChatProperties matrixProperties, boolean chatEnabled) {
        return service(
                matrixProperties,
                chatEnabled,
                new PlatformContractProperties(null, null, null, null, null, null, null, null));
    }

    private PlatformContractService service(
            MatrixChatProperties matrixProperties,
            boolean chatEnabled,
            PlatformContractProperties platformProperties) {
        return service(matrixProperties, chatEnabled, platformProperties, "https://api.weave.test", "api.weave.test");
    }

    private PlatformContractService service(
            MatrixChatProperties matrixProperties,
            boolean chatEnabled,
            PlatformContractProperties platformProperties,
            String facadeBaseUrl,
            String facadeServerName) {
        var settings = new Settings(
                new Settings.Capability(true, null, null),
                new Settings.Capability(chatEnabled, "https://matrix.weave.test", null),
                new Settings.Capability(true, "https://files.weave.test", null),
                new Settings.Capability(true, "https://files.weave.test", null), null, null);
        var security = new WeaveSecurityProperties("weave-app", "weave-app");
        var capabilities = WorkspaceCapabilityTestInstallation.service(
                resourceServerProperties(), security, settings);
        return new PlatformContractService(
                resourceServerProperties(), platformProperties, security,
                new com.massimotter.weave.backend.capability.config.WorkspaceCapabilityProperties(
                        WorkspaceCapabilityTestInstallation.convert(settings.shellAccess()),
                        WorkspaceCapabilityTestInstallation.convert(settings.adminControlPlane())),
                capabilities,
                java.util.List.of(new com.massimotter.weave.backend.chat.discovery.ChatMatrixPlatformContribution(
                        platformProperties, matrixProperties, capabilities, facadeBaseUrl, facadeServerName)),
                java.util.List.of(
                        new com.massimotter.weave.backend.files.discovery.FilesPlatformDiagnostics(capabilities),
                        new com.massimotter.weave.backend.calendar.discovery.CalendarPlatformDiagnostics(capabilities),
                        new com.massimotter.weave.backend.files.discovery.NextcloudPlatformDiagnostics(platformProperties)));
    }

    private OAuth2ResourceServerProperties resourceServerProperties() {
        OAuth2ResourceServerProperties properties = new OAuth2ResourceServerProperties();
        properties.getJwt().setIssuerUri("https://auth.weave.test/realms/weave");
        return properties;
    }
}
