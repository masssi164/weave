package com.massimotter.weave.backend.chat.application;

import com.massimotter.weave.backend.discovery.provider.ProviderPort;
import com.massimotter.weave.backend.discovery.provider.ProviderStatusResponse;
import com.massimotter.weave.backend.providerbinding.application.ProviderSelectionStatus;
import com.massimotter.weave.backend.providerbinding.selection.ProviderSelectionRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;

/** Chat status uses the same owning adapter and shared selection policy as platform diagnostics. */
public final class ChatProviderStatus {
    private final ProviderPort provider;
    private final ProviderSelectionRepository selections;

    public ChatProviderStatus(@Qualifier("chatProviderRegistrySeam") ProviderPort provider,
            ProviderSelectionRepository selections) {
        this.provider = java.util.Objects.requireNonNull(provider);
        this.selections = java.util.Objects.requireNonNull(selections);
    }

    public List<ProviderStatusResponse> status() {
        return List.of(ProviderSelectionStatus.project(provider.status(), "chat", selections.findByCategory("chat")));
    }
}
