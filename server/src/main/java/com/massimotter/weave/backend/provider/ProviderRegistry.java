package com.massimotter.weave.backend.provider;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ProviderRegistry {

    private final List<ProviderPort> providers;

    public ProviderRegistry(List<ProviderPort> providers) {
        this.providers = providers == null ? List.of() : List.copyOf(providers);
    }

    public ProviderRegistryResponse status() {
        List<ProviderStatusResponse> statuses = providers.stream()
                .map(ProviderPort::status)
                .sorted(Comparator
                        .comparing((ProviderStatusResponse status) -> status.module().contractName())
                        .thenComparing(ProviderStatusResponse::providerKey))
                .toList();
        return new ProviderRegistryResponse(
                "provider-stack-contract-preview",
                true,
                false,
                statuses.stream().allMatch(ProviderStatusResponse::supportSafe),
                Instant.now(),
                statuses);
    }
}
