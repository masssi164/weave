package com.massimotter.weave.backend.chat.provider.synapse;

import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.chat.port.CanonicalChatStore;
import com.massimotter.weave.backend.config.ChatRuntimeProperties;
import com.massimotter.weave.backend.portability.ProviderReadiness;
import java.time.Clock;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SynapseBackedCanonicalChatAdapterTest {

    @Test
    void conversationMappingFailuresDoNotOverrideAvailableProviderCapability() {
        CanonicalChatStore store = mock(CanonicalChatStore.class);
        MatrixSynapseChatSouthboundAdapter provider = mock(MatrixSynapseChatSouthboundAdapter.class);
        when(store.persistencePosture()).thenReturn("durable-relational-jpa-flyway");
        when(provider.providerKey()).thenReturn("matrix-synapse");
        when(provider.configured()).thenReturn(true);
        when(provider.readiness()).thenReturn(ProviderReadiness.ready("chat-provider-ready"));

        SynapseBackedCanonicalChatAdapter adapter = adapter(store, provider);

        assertThat(adapter.readiness())
                .isEqualTo(ProviderReadiness.ready("chat-provider-ready"));
    }

    @Test
    void systemicProviderFailureRemainsGlobalReadinessEvidence() {
        CanonicalChatStore store = mock(CanonicalChatStore.class);
        MatrixSynapseChatSouthboundAdapter provider = mock(MatrixSynapseChatSouthboundAdapter.class);
        when(store.persistencePosture()).thenReturn("durable-relational-jpa-flyway");
        when(provider.configured()).thenReturn(true);
        when(provider.readiness()).thenReturn(ProviderReadiness.degraded("chat-provider-authentication-failed"));

        SynapseBackedCanonicalChatAdapter adapter = adapter(store, provider);

        assertThat(adapter.readiness())
                .isEqualTo(ProviderReadiness.degraded("chat-provider-authentication-failed"));
    }

    @Test
    void systemicCallbackIntegrityFailureDegradesAnOtherwiseAvailableProvider() {
        CanonicalChatStore store = mock(CanonicalChatStore.class);
        MatrixSynapseChatSouthboundAdapter provider = mock(MatrixSynapseChatSouthboundAdapter.class);
        when(store.persistencePosture()).thenReturn("durable-relational-jpa-flyway");
        when(store.systemicCallbackIntegrityFailureCount("matrix-synapse")).thenReturn(1L);
        when(provider.providerKey()).thenReturn("matrix-synapse");
        when(provider.configured()).thenReturn(true);
        when(provider.readiness()).thenReturn(ProviderReadiness.ready("chat-provider-ready"));

        SynapseBackedCanonicalChatAdapter adapter = adapter(store, provider);

        assertThat(adapter.readiness())
                .isEqualTo(ProviderReadiness.degraded("chat-provider-ledger-semantic-mismatch"));
    }

    private SynapseBackedCanonicalChatAdapter adapter(
            CanonicalChatStore store,
            MatrixSynapseChatSouthboundAdapter provider) {
        return new SynapseBackedCanonicalChatAdapter(
                store,
                provider,
                ChatRuntimeProperties.Matrix.defaults(),
                tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build(),
                Clock.systemUTC());
    }
}
