package com.massimotter.weave.backend.chat.provider.synapse;

import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.chat.port.CanonicalChatStore;
import com.massimotter.weave.backend.config.ChatRuntimeProperties;
import com.massimotter.weave.backend.providerbinding.contract.ProviderReadiness;
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
        when(store.persistencePosture()).thenReturn("durable-relational-jpa-code-first");
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
        when(store.persistencePosture()).thenReturn("durable-relational-jpa-code-first");
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
        when(store.persistencePosture()).thenReturn("durable-relational-jpa-code-first");
        when(store.systemicCallbackIntegrityFailureCount("matrix-synapse")).thenReturn(1L);
        when(provider.providerKey()).thenReturn("matrix-synapse");
        when(provider.configured()).thenReturn(true);
        when(provider.readiness()).thenReturn(ProviderReadiness.ready("chat-provider-ready"));

        SynapseBackedCanonicalChatAdapter adapter = adapter(store, provider);

        assertThat(adapter.readiness())
                .isEqualTo(ProviderReadiness.degraded("chat-provider-ledger-semantic-mismatch"));
    }

    @Test
    void unqualifiedDirectAdmissionFailsBeforeAnyProviderOrCanonicalPreparation() {
        var store = mock(CanonicalChatStore.class);
        var provider = mock(MatrixSynapseChatSouthboundAdapter.class);
        when(store.persistencePosture()).thenReturn("durable-relational-jpa-code-first");
        var adapter = adapter(store, provider);
        org.mockito.Mockito.clearInvocations(store, provider);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> adapter.createConversation(
                com.massimotter.weave.backend.chat.domain.ChatRequestContext.isolatedTest(
                        new com.massimotter.weave.backend.chat.domain.ChatActorRef("user:author")),
                new com.massimotter.weave.backend.chat.domain.ChatTransactionId("direct-unqualified"),
                "Direct", "direct", java.util.List.of(),
                com.massimotter.weave.backend.chat.domain.ChatEncryptionState.unencrypted()))
                .isInstanceOf(com.massimotter.weave.backend.chat.domain.ChatProviderUnavailableException.class);
        org.mockito.Mockito.verifyNoInteractions(store, provider);
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
