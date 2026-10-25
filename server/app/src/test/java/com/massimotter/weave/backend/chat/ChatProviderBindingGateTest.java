package com.massimotter.weave.backend.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.massimotter.weave.backend.chat.port.ChatProviderPort;
import com.massimotter.weave.backend.providerbinding.domain.ProviderBinding;
import com.massimotter.weave.backend.providerbinding.port.ProviderBindingRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChatProviderBindingGateTest {
    private final ProviderBindingRepository bindings = mock(ProviderBindingRepository.class);
    private final ChatProviderPort adapter = mock(ChatProviderPort.class);
    private final ChatProviderBindingGate gate = new ChatProviderBindingGate(bindings, adapter);

    @Test
    void onlyTheCurrentMatchingOrganizationAndAdapterCanReceiveChatTraffic() {
        when(adapter.providerKey()).thenReturn("weave-native");
        assertThat(gate.admits("org:one")).isFalse();

        when(bindings.current("org:one", "chat")).thenReturn(Optional.of(binding("org:two", "weave-native")));
        assertThat(gate.admits("org:one")).isFalse();

        when(bindings.current("org:one", "chat")).thenReturn(Optional.of(binding("org:one", "matrix-synapse")));
        assertThat(gate.admits("org:one")).isFalse();

        when(bindings.current("org:one", "chat")).thenReturn(Optional.of(binding("org:one", "weave-native")));
        assertThat(gate.admits("org:one")).isTrue();
        assertThat(gate.admits("org:two")).isFalse();
    }

    private ProviderBinding binding(String organization, String adapterKey) {
        return new ProviderBinding(organization, "chat", 1, adapterKey, "profile:" + adapterKey,
                ProviderBinding.State.ACTIVE, Instant.EPOCH);
    }
}
