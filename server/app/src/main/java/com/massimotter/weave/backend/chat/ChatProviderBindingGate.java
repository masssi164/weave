package com.massimotter.weave.backend.chat;

import com.massimotter.weave.backend.chat.port.ChatProviderPort;
import com.massimotter.weave.backend.providerbinding.domain.ProviderBinding;
import com.massimotter.weave.backend.providerbinding.port.ProviderBindingRepository;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Admits Chat traffic only for the configured organization's durable active authority. */
@Component
public final class ChatProviderBindingGate {
    private final ProviderBindingRepository bindings;
    private final ChatProviderPort adapter;

    public ChatProviderBindingGate(ProviderBindingRepository bindings, ChatProviderPort adapter) {
        this.bindings = Objects.requireNonNull(bindings);
        this.adapter = Objects.requireNonNull(adapter);
    }

    public boolean admits(String organizationRef) {
        if (organizationRef == null || organizationRef.isBlank()) {
            return false;
        }
        return bindings.current(organizationRef, "chat")
                .filter(binding -> binding.state() == ProviderBinding.State.ACTIVE)
                .filter(binding -> organizationRef.equals(binding.organizationRef()))
                .filter(binding -> "chat".equals(binding.domain()))
                .filter(binding -> adapter.providerKey().equals(binding.adapterKey()))
                .filter(binding -> ("profile:" + adapter.providerKey()).equals(binding.configurationRef()))
                .isPresent();
    }
}
