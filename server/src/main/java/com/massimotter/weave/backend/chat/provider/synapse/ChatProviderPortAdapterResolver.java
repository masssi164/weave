package com.massimotter.weave.backend.chat.provider.synapse;

import com.massimotter.weave.backend.chat.port.ChatProviderPort;
import org.springframework.stereotype.Component;

@Component
public final class ChatProviderPortAdapterResolver {

    private final ChatProviderPort providerPort;

    public ChatProviderPortAdapterResolver(ChatProviderPort providerPort) {
        this.providerPort = providerPort;
    }

    public SynapseBackedCanonicalChatAdapter synapseAdapter() {
        if (providerPort instanceof SynapseBackedCanonicalChatAdapter adapter) {
            return adapter;
        }
        throw new IllegalStateException("Matrix/Synapse Chat adapter is not selected.");
    }
}
