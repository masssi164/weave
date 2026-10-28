package com.massimotter.weave.backend.chat.discovery;

import com.massimotter.weave.backend.api.error.ApiErrorException;
import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import com.massimotter.weave.backend.capability.config.CapabilitySettings;
import com.massimotter.weave.backend.chat.config.MatrixChatProperties;
import com.massimotter.weave.backend.discovery.api.PlatformStatusResponse;
import com.massimotter.weave.backend.discovery.config.PlatformContractProperties;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatMatrixPlatformContributionTest {
    private final PlatformStatusResponse.DiagnosticStatus auth =
            new PlatformStatusResponse.DiagnosticStatus("up", "ready", "Configured", null);

    @Test
    void completeCryptoProofIsStillDisabledWhenChatIsDisabled() {
        var contribution = contribution(false, "https://api.weave.test", "api.weave.test");
        var status = contribution.status(auth);
        assertThat(status.e2ee().status()).isEqualTo("validated");
        assertThat(status.e2eeEnabled()).isFalse();
        assertThat(status.readiness()).isEqualTo("unavailable");
        assertThat(status.backendBoundary().serverReadableMessageContent()).isFalse();
    }

    @Test
    void wrongFacadeIdentityBlocksReadinessWithoutChangingAdvertisedRoute() {
        var contribution = contribution(true, "https://api.weave.test", "wrong.example");
        assertThat(contribution.advertisedBaseUrl()).isEqualTo("https://api.weave.test");
        assertThat(contribution.status(auth).readiness()).isEqualTo("blocked");
        assertThat(contribution.status(auth).e2eeEnabled()).isFalse();
    }

    @Test
    void unsafeFacadeNeverProjectsCredentialsOrPath() {
        for (String route : java.util.List.of("https://member:secret@api.weave.test", "https://api.weave.test/_matrix")) {
            assertThatThrownBy(() -> contribution(true, route, "api.weave.test").advertisedBaseUrl())
                    .isInstanceOf(ApiErrorException.class).hasMessageNotContaining("secret");
        }
    }

    @Test
    void fullProofAndConsistentOriginExposeOnlyMetadataBoundary() {
        var status = contribution(true, "https://api.weave.test/", "api.weave.test").status(auth);
        assertThat(status.readiness()).isEqualTo("ready");
        assertThat(status.e2eeEnabled()).isTrue();
        assertThat(status.federationEnabled()).isFalse();
        assertThat(status.backendBoundary().metadataReadable()).doesNotContain("body", "plaintext");
    }

    private ChatMatrixPlatformContribution contribution(boolean enabled, String route, String serverName) {
        var capabilities = mock(WorkspaceCapabilityService.class);
        when(capabilities.settings("chat")).thenReturn(Optional.of(
                new CapabilitySettings(enabled, "https://matrix.example", null)));
        return new ChatMatrixPlatformContribution(
                new PlatformContractProperties(null, null, null, null, null, null, null, null),
                new MatrixChatProperties(false,
                        new MatrixChatProperties.E2ee(true, true, true, true, true, true, "verified"), null),
                capabilities, route, serverName);
    }
}
