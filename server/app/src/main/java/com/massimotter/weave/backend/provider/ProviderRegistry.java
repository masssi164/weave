package com.massimotter.weave.backend.provider;

import com.massimotter.weave.backend.providerbinding.selection.ProviderSelection;
import com.massimotter.weave.backend.providerbinding.application.ProviderSelectionStatus;
import com.massimotter.weave.backend.providerbinding.selection.ProviderSelectionRepository;
import com.massimotter.weave.backend.discovery.provider.ProviderStatusResponse;
import com.massimotter.weave.backend.discovery.provider.ProviderPort;
import com.massimotter.weave.backend.domainregistry.CanonicalDomainRegistry;
import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ProviderRegistry {

    public static final String PROVIDER_CONFIG_SOURCE = ProviderSelectionStatus.CONFIGURATION_SOURCE;

    private final List<ProviderPort> providers;
    private final WorkspaceCapabilityService workspaceCapabilityService;
    private final ProviderSelectionRepository selectionRepository;

    public ProviderRegistry(
            List<ProviderPort> providers,
            WorkspaceCapabilityService workspaceCapabilityService,
            ProviderSelectionRepository selectionRepository) {
        this.providers = providers == null ? List.of() : List.copyOf(providers);
        this.workspaceCapabilityService = workspaceCapabilityService;
        this.selectionRepository = selectionRepository;
    }

    public ProviderRegistryResponse status() {
        List<ProviderSelection> selections = selectionRepository.findAll();
        List<ProviderStatusResponse> statuses = providers.stream()
                .map(ProviderPort::status)
                .map(status -> applyAdminSelection(status, selections))
                .sorted(Comparator
                        .comparing((ProviderStatusResponse status) -> status.module().contractName())
                        .thenComparing(ProviderStatusResponse::providerKey))
                .toList();
        Instant generatedAt = Instant.now();
        List<ProviderCategoryStatusResponse> categories = ProviderCategoryHealthMapper.categories(
                statuses,
                workspaceCapabilityService.snapshot(),
                selectionRepository,
                generatedAt);
        return new ProviderRegistryResponse(
                "provider-stack-contract-v1",
                PROVIDER_CONFIG_SOURCE,
                true,
                true,
                true,
                false,
                statuses.stream().allMatch(ProviderStatusResponse::supportSafe),
                generatedAt,
                CanonicalDomainRegistry.snapshot(),
                DomainAdapterRegistryMapper.fromCategories(categories, generatedAt),
                selections,
                categories,
                statuses,
                null,
                null);
    }

    private ProviderStatusResponse applyAdminSelection(ProviderStatusResponse status, List<ProviderSelection> selections) {
        return ProviderCategoryCatalog.categoryForModule(status.module())
                .map(category -> ProviderSelectionStatus.project(status, category, selections.stream()
                        .filter(selection -> selection.category().equals(category)).findFirst()))
                .orElseGet(() -> ProviderSelectionStatus.unassigned(status));
    }
}
