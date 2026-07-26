package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.config.MatrixChatProperties;
import com.massimotter.weave.backend.config.PlatformContractProperties;
import com.massimotter.weave.backend.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformContractServiceTest {

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
    void exposesWeaveOwnedProtocolFacadesWithoutSouthboundProviderUrls() {
        PlatformContractService service = service(
                new MatrixChatProperties(false, null, null),
                true,
                new PlatformContractProperties(
                        "https://weave.test",
                        "https://api.weave.test/api",
                        "https://auth.weave.test",
                        "https://matrix.weave.test",
                        "https://weave.test/files",
                        "https://weave.test/calendar",
                        "https://files.weave.test",
                        null));

        var config = service.config();
        var status = service.status("nextcloud-route-test");

        assertThat(config.protocols().filesWebDavBaseUrl()).isEqualTo("https://api.weave.test/dav/files");
        assertThat(config.protocols().calendarCalDavBaseUrl()).isEqualTo("https://api.weave.test/caldav");
        assertThat(status.nextcloud().readiness()).isEqualTo("ready");
        assertThat(status.nextcloud().message()).contains("Nextcloud");
    }

    @Test
    void respectsConfiguredMatrixFacadeUrlAndNormalizesDefaultProjectionUrl() {
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
                .isEqualTo("https://chat.weave.test");
        assertThat(derived.config().protocols().matrixClientServerBaseUrl())
                .isEqualTo("https://api.weave.test");
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
        return new PlatformContractService(
                resourceServerProperties(),
                platformProperties,
                matrixProperties,
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new WorkspaceCapabilityProperties(
                        new WorkspaceCapabilityProperties.Capability(true, null, null),
                        new WorkspaceCapabilityProperties.Capability(chatEnabled, "https://matrix.weave.test", null),
                        new WorkspaceCapabilityProperties.Capability(true, "https://files.weave.test", null),
                        new WorkspaceCapabilityProperties.Capability(true, "https://files.weave.test", null),
                        null,
                        null));
    }

    private OAuth2ResourceServerProperties resourceServerProperties() {
        OAuth2ResourceServerProperties properties = new OAuth2ResourceServerProperties();
        properties.getJwt().setIssuerUri("https://auth.weave.test/realms/weave");
        return properties;
    }
}
