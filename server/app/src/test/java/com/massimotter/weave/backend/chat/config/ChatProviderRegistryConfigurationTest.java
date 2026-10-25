package com.massimotter.weave.backend.chat.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.massimotter.weave.backend.chat.port.ChatProviderPort;
import com.massimotter.weave.backend.providerbinding.contract.ProviderConformanceProfile;
import com.massimotter.weave.backend.discovery.provider.ProviderState;
import com.massimotter.weave.backend.discovery.provider.ProviderStatusResponse;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ChatProviderRegistryConfigurationTest {
    private final ChatProviderRegistryConfiguration configuration = new ChatProviderRegistryConfiguration();

    @Test
    void chatRegistryReportsTheBoundNativeAdapterWithoutInflatingReadiness() {
        ChatProviderPort runtime = mock(ChatProviderPort.class);
        when(runtime.providerKey()).thenReturn("weave-native");
        when(runtime.configured()).thenReturn(true);
        when(runtime.conformanceProfile()).thenReturn(new ProviderConformanceProfile(
                "chat",
                "weave-native",
                java.util.Set.of("timeline", "send"),
                java.util.Map.of(),
                true,
                true,
                true));

        ProviderStatusResponse status = configuration.chatProviderRegistrySeamFor(runtime).status();

        assertThat(status.providerKey()).isEqualTo("weave-native");
        assertThat(status.state()).isEqualTo(ProviderState.CONFIGURED);
        assertThat(status.readiness()).isEqualTo("configured_pending_cached_health");
        assertThat(status.candidates()).contains("weave-native", "matrix-synapse");
        assertThat(status.diagnostics())
                .containsEntry("runtimeBindingObserved", true)
                .containsEntry("secretsReturned", false)
                .containsEntry("rawProviderErrorsReturned", false);
    }
}
