package com.massimotter.weave.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.massimotter.weave.backend.boards.local.LocalWorkspaceBoardsRepository;
import com.massimotter.weave.backend.provider.ProviderRealityLevel;
import com.massimotter.weave.backend.provider.ProviderState;
import com.massimotter.weave.backend.provider.ProviderStatusResponse;
import org.junit.jupiter.api.Test;

class ProviderCoreConfigurationTest {

    private final ProviderCoreConfiguration configuration = new ProviderCoreConfiguration();

    @Test
    void boardsRegistryReportsTheActuallyBoundLocalWorkspaceAdapter() {
        ProviderStatusResponse status = configuration
                .boardsProviderRegistrySeamFor(new LocalWorkspaceBoardsRepository())
                .status();

        assertThat(status.providerKey()).isEqualTo("local-workspace");
        assertThat(status.state()).isEqualTo(ProviderState.CONFIGURED);
        assertThat(status.configured()).isTrue();
        assertThat(status.providerRealityLevel()).isEqualTo(ProviderRealityLevel.CONFIGURED);
        assertThat(status.candidates()).contains("openproject-primary", "local-workspace");
        assertThat(status.diagnostics())
                .containsEntry("runtimeBindingObserved", true)
                .containsEntry("runtimeAdapterKind", "local-workspace")
                .containsEntry("secretsReturned", false)
                .containsEntry("rawProviderErrorsReturned", false);
        assertThat(status.summary()).doesNotContain("primary workspace-sync provider");
    }

    @Test
    void liveKitSfuAdapterReportsDirectCredentialModeSupportSafely() {
        ProviderStatusResponse status = configuration.liveKitSfuProviderRegistrySeam(
                new LiveKitSfuProviderProperties(
                        true,
                        "https://livekit.internal",
                        "secret-api-key",
                        "secret-api-secret",
                        ""))
                .status();

        assertThat(status.module().contractName()).isEqualTo("meetings");
        assertThat(status.providerKey()).isEqualTo("livekit");
        assertThat(status.state()).isEqualTo(ProviderState.CONFIGURED);
        assertThat(status.readiness()).isEqualTo("configured");
        assertThat(status.enabled()).isTrue();
        assertThat(status.configured()).isTrue();
        assertThat(status.failClosed()).isTrue();
        assertThat(status.supportSafe()).isTrue();
        assertThat(status.diagnostics())
                .containsEntry("livekitUrlConfigured", true)
                .containsEntry("apiKeyConfigured", true)
                .containsEntry("apiSecretConfigured", true)
                .containsEntry("directCredentialModeConfigured", true)
                .containsEntry("tokenEndpointModeConfigured", false)
                .containsEntry("secretsReturned", false);
        assertThat(status.toString())
                .doesNotContain("secret-api-key")
                .doesNotContain("secret-api-secret")
                .doesNotContain("https://livekit.internal");
    }

    @Test
    void liveKitSfuAdapterCanUseTokenEndpointModeWithoutReturningEndpointValue() {
        ProviderStatusResponse status = configuration.liveKitSfuProviderRegistrySeam(
                new LiveKitSfuProviderProperties(
                        true,
                        "https://livekit.internal",
                        "",
                        "",
                        "https://token-broker.internal/livekit"))
                .status();

        assertThat(status.state()).isEqualTo(ProviderState.CONFIGURED);
        assertThat(status.configured()).isTrue();
        assertThat(status.diagnostics())
                .containsEntry("tokenEndpointConfigured", true)
                .containsEntry("directCredentialModeConfigured", false)
                .containsEntry("tokenEndpointModeConfigured", true)
                .containsEntry("secretsReturned", false);
        assertThat(status.toString())
                .doesNotContain("https://livekit.internal")
                .doesNotContain("https://token-broker.internal/livekit");
    }
}
