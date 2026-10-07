package com.massimotter.weave.backend.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.massimotter.weave.backend.chat.port.ChatProviderPort;
import com.massimotter.weave.backend.providerbinding.domain.ProviderBinding;
import com.massimotter.weave.backend.providerbinding.port.ProviderBindingRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChatBindingBootstrapConfigurationTest {
    private final ProviderBindingRepository bindings = mock(ProviderBindingRepository.class);
    private final ChatProviderPort adapter = mock(ChatProviderPort.class);
    private final ContextAuthorizationProperties context = new ContextAuthorizationProperties(
            null, null, "org:one", null, null, null, null, null);
    private final ChatBindingBootstrapConfiguration configuration = new ChatBindingBootstrapConfiguration();

    @Test
    void firstStartCreatesOnlyTheConfiguredOrganizationsChatBinding() throws Exception {
        when(adapter.providerKey()).thenReturn("weave-native");
        when(bindings.current("org:one", "chat")).thenReturn(Optional.empty());

        configuration.chatBindingBootstrap(bindings, context, adapter).run(null);

        verify(bindings).activate(eq("org:one"), eq("chat"), eq(0L), eq("weave-native"),
                eq("profile:weave-native"), any());
    }

    @Test
    void restartRejectsAChangedAdapterWithoutOverwritingTheCurrentAuthority() {
        when(adapter.providerKey()).thenReturn("matrix-synapse");
        when(bindings.current("org:one", "chat")).thenReturn(Optional.of(new ProviderBinding(
                "org:one", "chat", 1, "weave-native", "profile:weave-native",
                ProviderBinding.State.ACTIVE, Instant.EPOCH)));

        assertThatThrownBy(() -> configuration.chatBindingBootstrap(bindings, context, adapter).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("conflicts");
        verify(bindings, never()).activate(anyString(), anyString(), anyLong(), anyString(), anyString(), any());
    }
}
