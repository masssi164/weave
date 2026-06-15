package com.massimotter.weave.backend.provider;

import com.massimotter.weave.backend.domainregistry.CanonicalDomainRegistry;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ProviderRegistry {

    public static final String PROVIDER_CONFIG_SOURCE = "admin-control-plane-selected-provider-mappings";

    private final List<ProviderPort> providers;
    private final WorkspaceCapabilityService workspaceCapabilityService;
    private final ProviderSelectionRepository selectionRepository;
    private final DomainBindingService domainBindingService;

    @Autowired
    public ProviderRegistry(
            List<ProviderPort> providers,
            WorkspaceCapabilityService workspaceCapabilityService,
            ProviderSelectionRepository selectionRepository) {
        this(providers, workspaceCapabilityService, selectionRepository, new DomainBindingService());
    }

    ProviderRegistry(
            List<ProviderPort> providers,
            WorkspaceCapabilityService workspaceCapabilityService,
            ProviderSelectionRepository selectionRepository,
            DomainBindingService domainBindingService) {
        this.providers = providers == null ? List.of() : List.copyOf(providers);
        this.workspaceCapabilityService = workspaceCapabilityService;
        this.selectionRepository = selectionRepository;
        this.domainBindingService = domainBindingService == null ? new DomainBindingService() : domainBindingService;
    }

    public ProviderRegistryResponse status() {
        List<ProviderSelection> selections = selectionRepository.findAll();
        List<ProviderStatusResponse> statuses = providerStatuses(selections);
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
                statuses);
    }

    public DomainBindingsResponse domainBindings(String requestedDomainKey) {
        return domainBindingService.bindings(status(), requestedDomainKey);
    }

    private List<ProviderStatusResponse> providerStatuses(List<ProviderSelection> selections) {
        return providers.stream()
                .map(ProviderPort::status)
                .map(status -> applyAdminSelection(status, selections))
                .sorted(Comparator
                        .comparing((ProviderStatusResponse status) -> status.module().contractName())
                        .thenComparing(ProviderStatusResponse::providerKey))
                .toList();
    }


    private ProviderStatusResponse applyAdminSelection(ProviderStatusResponse status, List<ProviderSelection> selections) {
        Optional<String> maybeCategory = ProviderCategoryCatalog.categoryForModule(status.module());
        if (maybeCategory.isEmpty()) {
            return withDiagnostics(status, Map.of(
                    "providerConfigSource", PROVIDER_CONFIG_SOURCE,
                    "selectedByAdmin", false,
                    "bootstrapSuggestionOnly", true));
        }
        String category = maybeCategory.get();
        Optional<ProviderSelection> maybeSelection = selections.stream()
                .filter(selection -> selection.category().equals(category))
                .findFirst();
        if (maybeSelection.isEmpty()) {
            return new ProviderStatusResponse(
                    status.module(),
                    status.providerKey(),
                    ProviderState.DISABLED,
                    "awaiting_admin_selection",
                    false,
                    false,
                    status.readOnly(),
                    true,
                    status.supportSafe(),
                    status.paidFeaturesRequired(),
                    status.summary() + " Bootstrap/profile default only; Admin Console has not selected this category mapping.",
                    status.supportedCapabilities(),
                    status.unsupportedOperations(),
                    status.supportSafeErrorCodes(),
                    status.redactionPolicy(),
                    status.candidates(),
                    status.providerRealityLevel(),
                    diagnostics(status, Map.of(
                            "category", category,
                            "providerConfigSource", PROVIDER_CONFIG_SOURCE,
                            "selectedByAdmin", false,
                            "bootstrapSuggestionOnly", true,
                            "selectionRequiredBeforeProviderUse", true)));
        }
        ProviderSelection selection = maybeSelection.get();
        boolean selectedProvider = selection.providerKey().equals(status.providerKey())
                || status.candidates().stream().map(value -> value.toLowerCase(Locale.ROOT)).anyMatch(value -> value.equals(selection.providerKey().toLowerCase(Locale.ROOT)));
        if (!selectedProvider) {
            return new ProviderStatusResponse(
                    status.module(),
                    status.providerKey(),
                    ProviderState.DISABLED,
                    "not_selected_by_admin",
                    false,
                    false,
                    status.readOnly(),
                    true,
                    status.supportSafe(),
                    status.paidFeaturesRequired(),
                    status.summary() + " This adapter candidate is not the Admin Console-selected mapping for " + category + ".",
                    status.supportedCapabilities(),
                    status.unsupportedOperations(),
                    status.supportSafeErrorCodes(),
                    status.redactionPolicy(),
                    status.candidates(),
                    status.providerRealityLevel(),
                    diagnostics(status, Map.of(
                            "category", category,
                            "providerConfigSource", PROVIDER_CONFIG_SOURCE,
                            "selectedByAdmin", false,
                            "selectedCategoryProviderKey", selection.providerKey(),
                            "bootstrapSuggestionOnly", false)));
        }
        boolean configured = status.configured();
        ProviderState state = configured ? status.state() : ProviderState.NOT_CONFIGURED;
        String readiness = configured ? status.readiness() : "admin_selected_pending_backend_configuration";
        return new ProviderStatusResponse(
                status.module(),
                status.providerKey(),
                state,
                readiness,
                true,
                configured,
                status.readOnly(),
                true,
                true,
                status.paidFeaturesRequired(),
                status.summary() + " Admin Console selection is the source of truth; SecretRefs are control-plane handles only and readiness remains backend-owned/support-safe.",
                status.supportedCapabilities(),
                status.unsupportedOperations(),
                status.supportSafeErrorCodes(),
                status.redactionPolicy(),
                status.candidates(),
                status.providerRealityLevel(),
                diagnostics(status, Map.of(
                        "category", category,
                        "providerConfigSource", PROVIDER_CONFIG_SOURCE,
                        "selectedByAdmin", true,
                        "choiceModel", selection.choiceModel(),
                        "bootstrapSuggestionOnly", false,
                        "secretRefConfigured", selection.hasSecretRef(),
                        "secretsReturned", false,
                        "rawProviderErrorsReturned", false,
                        "migrationDryRunRequired", selection.migrationDryRunRequired(),
                        "lossyMappingNoteCount", selection.lossyMappingNotes().size())));
    }

    private ProviderStatusResponse withDiagnostics(ProviderStatusResponse status, Map<String, Object> extra) {
        return new ProviderStatusResponse(
                status.module(), status.providerKey(), status.state(), status.readiness(), status.enabled(), status.configured(),
                status.readOnly(), status.failClosed(), status.supportSafe(), status.paidFeaturesRequired(), status.summary(),
                status.supportedCapabilities(), status.unsupportedOperations(), status.supportSafeErrorCodes(), status.redactionPolicy(),
                status.candidates(), status.providerRealityLevel(), diagnostics(status, extra));
    }

    private Map<String, Object> diagnostics(ProviderStatusResponse status, Map<String, Object> extra) {
        Map<String, Object> diagnostics = new LinkedHashMap<>(status.diagnostics());
        diagnostics.put("secretsReturned", false);
        diagnostics.put("rawProviderErrorsReturned", false);
        diagnostics.put("providerRealityLevel", status.providerRealityLevel().value());
        diagnostics.putAll(extra);
        return diagnostics;
    }
}
