package com.massimotter.weave.backend.provider;

import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ProviderRegistry {

    private final List<ProviderPort> providers;
    private final WorkspaceCapabilityService workspaceCapabilityService;

    public ProviderRegistry(List<ProviderPort> providers, WorkspaceCapabilityService workspaceCapabilityService) {
        this.providers = providers == null ? List.of() : List.copyOf(providers);
        this.workspaceCapabilityService = workspaceCapabilityService;
    }

    public ProviderRegistryResponse status() {
        List<ProviderStatusResponse> statuses = providers.stream()
                .map(ProviderPort::status)
                .sorted(Comparator
                        .comparing((ProviderStatusResponse status) -> status.module().contractName())
                        .thenComparing(ProviderStatusResponse::providerKey))
                .toList();
        Instant generatedAt = Instant.now();
        List<ProviderCategoryStatusResponse> categories = ProviderCategoryHealthMapper.categories(
                statuses,
                workspaceCapabilityService.snapshot());
        return new ProviderRegistryResponse(
                "provider-stack-contract-preview",
                true,
                false,
                statuses.stream().allMatch(ProviderStatusResponse::supportSafe),
                generatedAt,
                DomainAdapterRegistryMapper.fromCategories(categories, generatedAt),
                categories,
                statuses);
    }
}
