package com.massimotter.weave.backend.chat.domain;

import com.massimotter.weave.backend.providerbinding.port.ProviderBindingTransitionPolicy;

/** Preserves canonical identity until provider reconciliation is explicitly supported. */
public final class ChatProviderBindingPolicy implements ProviderBindingTransitionPolicy {
    @Override
    public String domain() { return "chat"; }

    @Override
    public void validateReplacement(boolean hasMappings) {
        throw new BindingTransitionBlockedException();
    }

    public static final class BindingTransitionBlockedException extends RuntimeException {
        public BindingTransitionBlockedException() { super("Chat binding transition requires verified provider reconciliation"); }
    }
}
