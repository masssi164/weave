package com.massimotter.weave.backend.discovery.application;

import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import com.massimotter.weave.backend.discovery.api.ProviderCategoryStatusResponse;
import com.massimotter.weave.backend.discovery.api.ProviderRegistryResponse;
import com.massimotter.weave.backend.discovery.catalog.CanonicalDomainRegistry;
import com.massimotter.weave.backend.discovery.catalog.ProviderCapabilityContracts;
import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryCatalog;
import com.massimotter.weave.backend.discovery.provider.ProviderPort;
import com.massimotter.weave.backend.discovery.provider.ProviderStatusResponse;
import com.massimotter.weave.backend.providerbinding.application.ProviderSelectionStatus;
import com.massimotter.weave.backend.providerbinding.selection.ProviderSelection;
import com.massimotter.weave.backend.providerbinding.selection.ProviderSelectionRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public class ProviderRegistry {

    public static final String PROVIDER_CONFIG_SOURCE = ProviderSelectionStatus.CONFIGURATION_SOURCE;

    private final List<ProviderPort> providers;
    private final WorkspaceCapabilityService workspaceCapabilityService;
    private final ProviderSelectionRepository selectionRepository;
    private final ProviderCategoryCatalog catalog;
    private final ProviderCapabilityContracts contracts;
    private final CanonicalDomainRegistry canonicalDomains;

    public ProviderRegistry(
            List<ProviderPort> providers,
            WorkspaceCapabilityService workspaceCapabilityService,
            ProviderSelectionRepository selectionRepository,
            ProviderCategoryCatalog catalog,
            ProviderCapabilityContracts contracts,
            CanonicalDomainRegistry canonicalDomains) {
        this.providers = List.copyOf(providers);
        this.workspaceCapabilityService = java.util.Objects.requireNonNull(workspaceCapabilityService, "workspaceCapabilityService");
        this.selectionRepository = java.util.Objects.requireNonNull(selectionRepository, "selectionRepository");
        this.catalog = java.util.Objects.requireNonNull(catalog, "catalog");
        this.contracts = java.util.Objects.requireNonNull(contracts, "contracts");
        this.canonicalDomains = java.util.Objects.requireNonNull(canonicalDomains, "canonicalDomains");
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
        var capabilityStates = new java.util.LinkedHashMap<>(workspaceCapabilityService.installedSnapshots(null));
        capabilityStates.put("admin-control-plane", workspaceCapabilityService.adminControlPlaneSnapshot());
        List<ProviderCategoryStatusResponse> categories = new ProviderCategoryHealthMapper(contracts).categories(
                statuses, capabilityStates, selectionRepository, generatedAt, catalog);
        return new ProviderRegistryResponse(
                "provider-stack-contract-v1",
                PROVIDER_CONFIG_SOURCE,
                true,
                true,
                true,
                false,
                statuses.stream().allMatch(ProviderStatusResponse::supportSafe),
                generatedAt,
                canonicalDomains.snapshot(),
                DomainAdapterRegistryMapper.fromCategories(categories, generatedAt),
                selections,
                categories,
                statuses,
                null,
                null);
    }

    private ProviderStatusResponse applyAdminSelection(ProviderStatusResponse status, List<ProviderSelection> selections) {
        return catalog.categoryForModule(status.module())
                .map(category -> ProviderSelectionStatus.project(status, category, selections.stream()
                        .filter(selection -> selection.category().equals(category)).findFirst()))
                .orElseGet(() -> ProviderSelectionStatus.unassigned(status));
    }
}
