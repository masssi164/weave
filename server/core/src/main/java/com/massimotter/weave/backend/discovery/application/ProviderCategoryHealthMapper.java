package com.massimotter.weave.backend.discovery.application;

import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityPolicyState;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityStatusResponse;
import com.massimotter.weave.backend.discovery.api.ProviderAdapterReadinessEvidenceResponse;
import com.massimotter.weave.backend.discovery.api.ProviderCategoryReadiness;
import com.massimotter.weave.backend.discovery.api.ProviderCategoryStatusResponse;
import com.massimotter.weave.backend.discovery.catalog.ProviderCapabilityContracts;
import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryCatalog;
import com.massimotter.weave.backend.discovery.provider.ProviderModule;
import com.massimotter.weave.backend.discovery.provider.ProviderRealityLevel;
import com.massimotter.weave.backend.discovery.provider.ProviderState;
import com.massimotter.weave.backend.discovery.provider.ProviderStatusResponse;
import com.massimotter.weave.backend.providerbinding.selection.ProviderSelectionRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

final class ProviderCategoryHealthMapper {

    private final ProviderCapabilityContracts contracts;

    ProviderCategoryHealthMapper(ProviderCapabilityContracts contracts) {
        this.contracts = contracts;
    }

    List<ProviderCategoryStatusResponse> categories(
            List<ProviderStatusResponse> providers,
            Map<String, WorkspaceCapabilityStatusResponse> capabilities,
            ProviderSelectionRepository selections,
            Instant generatedAt,
            ProviderCategoryCatalog catalog) {
        List<ProviderCategoryStatusResponse> categories = new ArrayList<>();
        for (var definition : catalog.categories()) {
            if (definition.capabilityBacked()) {
                var capability = capabilities.get(definition.capabilityDomain());
                if (capability == null) {
                    throw new IllegalStateException("Installed provider category requires capability: "
                            + definition.capabilityDomain());
                }
                categories.add(capabilityCategory(definition.key(), definition.label(), capability,
                        providers, definition.modules(), selections, generatedAt));
            } else {
                categories.add(providerCategory(definition.key(), definition.label(), definition.memberImpact(),
                        providers, definition.modules(), selections, generatedAt));
            }
        }
        return List.copyOf(categories);
    }

    private ProviderCategoryStatusResponse capabilityCategory(
            String category,
            String label,
            WorkspaceCapabilityStatusResponse capability,
            List<ProviderStatusResponse> providers,
            Set<ProviderModule> modules,
            ProviderSelectionRepository selections,
            Instant evidenceTimestamp) {
        SelectionView selection = selectionView(category, selections);
        ProviderCategoryReadiness readiness = effectiveCapabilityReadiness(
                capability, providers, modules, selection.selectedByAdmin());
        ProviderRealityLevel realityLevel = categoryRealityLevel(category, providers, modules, selection);
        String memberState = memberCapabilityState(capability.policyState(), readiness, realityLevel, selection.selectedByAdmin() || modules.isEmpty());
        return new ProviderCategoryStatusResponse(
                category,
                label,
                contracts.contract(category, modules),
                readiness,
                realityLevel,
                memberState,
                realityLevelRemediation(realityLevel),
                capability.policyState(),
                selection.selectedByAdmin() || modules.isEmpty()
                        ? capability.memberImpact()
                        : "Admin provider mapping is required before this category becomes product-ready.",
                moduleNames(modules),
                providerCandidates(category, providers, modules),
                selection.providerKey(),
                selection.choiceModel(),
                selection.selectedByAdmin(),
                !selection.selectedByAdmin(),
                selection.lossyMappingNotes(),
                adapterEvidence(category, providers, modules, selection, readiness, evidenceTimestamp),
                diagnostics(providers, modules, Map.of(
                        "capabilityEnabled", capability.enabled(),
                        "capabilityReadiness", capability.readiness().value(),
                        "effectivePolicyState", capability.policyState().value(),
                        "grantedCapabilityCount", capability.grantedCapabilities().size(),
                        "providerConfigSource", ProviderRegistry.PROVIDER_CONFIG_SOURCE,
                        "selectionRequiredBeforeProviderUse", !selection.selectedByAdmin() && !modules.isEmpty(),
                        "diagnosticsRedacted", true)));
    }

    private ProviderCategoryStatusResponse providerCategory(
            String category,
            String label,
            String memberImpact,
            List<ProviderStatusResponse> providers,
            Set<ProviderModule> modules,
            ProviderSelectionRepository selections,
            Instant evidenceTimestamp) {
        List<ProviderStatusResponse> matching = matching(providers, modules);
        SelectionView selection = selectionView(category, selections);
        ProviderCategoryReadiness readiness = selection.selectedByAdmin()
                ? fromProviders(matching)
                : ProviderCategoryReadiness.MISCONFIGURED;
        WorkspaceCapabilityPolicyState policyState = readiness == ProviderCategoryReadiness.DISABLED
                ? WorkspaceCapabilityPolicyState.DISABLED
                : readiness == ProviderCategoryReadiness.MISCONFIGURED
                        ? WorkspaceCapabilityPolicyState.UNAVAILABLE
                        : WorkspaceCapabilityPolicyState.ALLOWED;
        ProviderRealityLevel realityLevel = categoryRealityLevel(category, providers, modules, selection);
        String memberState = memberCapabilityState(policyState, readiness, realityLevel, selection.selectedByAdmin());
        return new ProviderCategoryStatusResponse(
                category,
                label,
                contracts.contract(category, modules),
                readiness,
                realityLevel,
                memberState,
                realityLevelRemediation(realityLevel),
                policyState,
                selection.selectedByAdmin() ? memberImpact : "Admin provider mapping is required before this category becomes product-ready.",
                moduleNames(modules),
                providerCandidates(category, providers, modules),
                selection.providerKey(),
                selection.choiceModel(),
                selection.selectedByAdmin(),
                !selection.selectedByAdmin(),
                selection.lossyMappingNotes(),
                adapterEvidence(category, providers, modules, selection, readiness, evidenceTimestamp),
                diagnostics(providers, modules, Map.of(
                        "effectivePolicyState", policyState.value(),
                        "providerConfigSource", ProviderRegistry.PROVIDER_CONFIG_SOURCE,
                        "selectionRequiredBeforeProviderUse", !selection.selectedByAdmin(),
                        "diagnosticsRedacted", true)));
    }

    private ProviderRealityLevel categoryRealityLevel(
            String category,
            List<ProviderStatusResponse> providers,
            Set<ProviderModule> modules,
            SelectionView selection) {
        List<ProviderStatusResponse> matching = matching(providers, modules);
        if (matching.isEmpty()) {
            return contracts.defaultRealityLevel(category);
        }
        return matching.stream()
                .filter(provider -> !selection.selectedByAdmin()
                        || provider.providerKey().equals(selection.providerKey())
                        || provider.candidates().contains(selection.providerKey()))
                .map(ProviderStatusResponse::providerRealityLevel)
                .max(ProviderRealityLevel.priorityComparator())
                .orElse(ProviderRealityLevel.CONTRACT_ONLY);
    }

    private String memberCapabilityState(
            WorkspaceCapabilityPolicyState policyState,
            ProviderCategoryReadiness readiness,
            ProviderRealityLevel realityLevel,
            boolean selectedOrBuiltin) {
        if (policyState == WorkspaceCapabilityPolicyState.POLICY_BLOCKED
                || policyState == WorkspaceCapabilityPolicyState.DISABLED) {
            return "disabled_by_policy";
        }
        if (!selectedOrBuiltin) {
            return "not_configured";
        }
        if (readiness == ProviderCategoryReadiness.DEGRADED) {
            return "degraded";
        }
        if (readiness == ProviderCategoryReadiness.READY && realityLevel.canBeMemberAvailable()) {
            return "available";
        }
        if (realityLevel == ProviderRealityLevel.CONTRACT_ONLY) {
            return "coming_later";
        }
        if (readiness == ProviderCategoryReadiness.MISCONFIGURED) {
            return "not_configured";
        }
        return "unavailable";
    }

    private String realityLevelRemediation(ProviderRealityLevel realityLevel) {
        return switch (realityLevel) {
            case CONTRACT_ONLY -> "Contract-only candidate: keep member state unavailable/coming_later until adapter code, readiness evidence, and policy gates exist.";
            case CONFIGURED -> "Configuration/readiness candidate: finish backend adapter proof before claiming live member availability.";
            case LIVE_READ -> "Read adapter exists: prove write/delete boundaries, audit, and support-bundle redaction before broad availability.";
            case LIVE_WRITE -> "Read/write adapter exists: complete migration dry-run evidence before apply or rollback claims.";
            case MIGRATION_DRY_RUN -> "Migration dry-run is ready: prove apply safety before replacement claims.";
            case MIGRATION_APPLY_READY -> "Migration apply is ready: complete rollback evidence before availability claims.";
            case ROLLBACK_READY -> "Rollback is ready: complete release gate evidence before general availability claims.";
            case RELEASE_READY -> "Release-ready provider: keep policy, readiness, support-safe diagnostics, and release evidence current.";
        };
    }

    private ProviderCategoryReadiness fromCapability(WorkspaceCapabilityStatusResponse capability) {
        if (capability.policyState() == WorkspaceCapabilityPolicyState.POLICY_BLOCKED) {
            return ProviderCategoryReadiness.POLICY_BLOCKED;
        }
        if (capability.policyState() == WorkspaceCapabilityPolicyState.DISABLED || !capability.enabled()) {
            return ProviderCategoryReadiness.DISABLED;
        }
        return switch (capability.readiness()) {
            case READY -> ProviderCategoryReadiness.READY;
            case DEGRADED -> ProviderCategoryReadiness.DEGRADED;
            case BLOCKED -> ProviderCategoryReadiness.MISCONFIGURED;
            case UNAVAILABLE -> ProviderCategoryReadiness.MISCONFIGURED;
        };
    }

    private ProviderCategoryReadiness effectiveCapabilityReadiness(
            WorkspaceCapabilityStatusResponse capability,
            List<ProviderStatusResponse> providers,
            Set<ProviderModule> modules,
            boolean selectedByAdmin) {
        ProviderCategoryReadiness capabilityReadiness = fromCapability(capability);
        if (modules.isEmpty() || capabilityReadiness != ProviderCategoryReadiness.READY) {
            return capabilityReadiness;
        }
        if (!selectedByAdmin) {
            return ProviderCategoryReadiness.MISCONFIGURED;
        }
        return fromProviders(matching(providers, modules));
    }

    private ProviderCategoryReadiness fromProviders(List<ProviderStatusResponse> providers) {
        if (providers.isEmpty() || providers.stream().noneMatch(ProviderStatusResponse::enabled)) {
            return ProviderCategoryReadiness.DISABLED;
        }
        if (providers.stream().anyMatch(provider -> provider.enabled() && !provider.configured())) {
            return ProviderCategoryReadiness.MISCONFIGURED;
        }
        if (providers.stream().anyMatch(provider -> provider.state() == ProviderState.DEGRADED)) {
            return ProviderCategoryReadiness.DEGRADED;
        }
        if (providers.stream().anyMatch(provider -> provider.configured()
                && (provider.state() == ProviderState.READY || provider.state() == ProviderState.CONFIGURED))) {
            return ProviderCategoryReadiness.READY;
        }
        return ProviderCategoryReadiness.DEGRADED;
    }

    private List<ProviderAdapterReadinessEvidenceResponse> adapterEvidence(
            String category,
            List<ProviderStatusResponse> providers,
            Set<ProviderModule> modules,
            SelectionView selection,
            ProviderCategoryReadiness readiness,
            Instant evidenceTimestamp) {
        List<ProviderStatusResponse> matching = matching(providers, modules);
        if (matching.isEmpty()) {
            return List.of();
        }
        return matching.stream()
                .map(provider -> new ProviderAdapterReadinessEvidenceResponse(
                        category,
                        provider.providerKey(),
                        provider.configured(),
                        reachable(provider),
                        selection.selectedByAdmin() ? provider.readiness() : readiness.value(),
                        provider.providerRealityLevel(),
                        provider.failClosed(),
                        providerEvidenceDiagnostics(provider, selection),
                        evidenceTimestamp))
                .toList();
    }

    private boolean reachable(ProviderStatusResponse provider) {
        return provider.enabled()
                && provider.configured()
                && provider.state() == ProviderState.READY;
    }

    private Map<String, Object> providerEvidenceDiagnostics(
            ProviderStatusResponse provider,
            SelectionView selection) {
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("providerState", provider.state().contractName());
        diagnostics.put("configured", provider.configured());
        diagnostics.put("enabled", provider.enabled());
        diagnostics.put("selectedByAdmin", selection.selectedByAdmin());
        diagnostics.put("bootstrapSuggestionOnly", !selection.selectedByAdmin());
        diagnostics.put("supportSafe", provider.supportSafe());
        diagnostics.put("failClosed", provider.failClosed());
        diagnostics.put("providerRealityLevel", provider.providerRealityLevel().value());
        diagnostics.put("supportedCapabilityCount", provider.supportedCapabilities().size());
        diagnostics.put("unsupportedOperationCount", provider.unsupportedOperations().size());
        diagnostics.put("supportSafeErrorCodes", provider.supportSafeErrorCodes());
        diagnostics.put("secretsReturned", false);
        diagnostics.put("rawProviderErrorsReturned", false);
        diagnostics.put("diagnosticsRedacted", true);
        return diagnostics;
    }

    private Map<String, Object> diagnostics(
            List<ProviderStatusResponse> providers,
            Set<ProviderModule> modules,
            Map<String, Object> extra) {
        List<ProviderStatusResponse> matching = matching(providers, modules);
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("providerCount", matching.size());
        diagnostics.put("enabledProviderCount", matching.stream().filter(ProviderStatusResponse::enabled).count());
        diagnostics.put("configuredProviderCount", matching.stream().filter(ProviderStatusResponse::configured).count());
        diagnostics.put("allSupportSafe", matching.stream().allMatch(ProviderStatusResponse::supportSafe));
        diagnostics.put("allFailClosed", matching.stream().allMatch(ProviderStatusResponse::failClosed));
        diagnostics.put("providerRealityLevels", matching.stream().map(provider -> provider.providerRealityLevel().value()).distinct().sorted().toList());
        diagnostics.put("secretsReturned", false);
        diagnostics.put("rawProviderErrorsReturned", false);
        diagnostics.putAll(extra);
        return diagnostics;
    }

    private List<String> providerCandidates(String category, List<ProviderStatusResponse> providers, Set<ProviderModule> modules) {
        List<String> registeredCandidates = matching(providers, modules).stream()
                .flatMap(provider -> {
                    List<String> values = new ArrayList<>();
                    values.add(provider.providerKey());
                    values.addAll(provider.candidates());
                    return values.stream();
                })
                .distinct()
                .sorted()
                .toList();
        if (!registeredCandidates.isEmpty()) {
            return registeredCandidates;
        }
        return contracts.providerCandidates(category);
    }

    private List<String> moduleNames(Set<ProviderModule> modules) {
        return modules.stream()
                .map(ProviderModule::contractName)
                .sorted()
                .toList();
    }

    private List<ProviderStatusResponse> matching(List<ProviderStatusResponse> providers, Set<ProviderModule> modules) {
        Predicate<ProviderStatusResponse> predicate = modules.isEmpty()
                ? provider -> false
                : provider -> modules.contains(provider.module());
        return providers.stream()
                .filter(predicate)
                .sorted(Comparator.comparing(provider -> provider.module().contractName()))
                .toList();
    }

    private SelectionView selectionView(String category, ProviderSelectionRepository selections) {
        if (selections == null) {
            return SelectionView.awaiting();
        }
        return selections.findByCategory(category)
                .map(selection -> new SelectionView(
                        selection.providerKey(),
                        selection.choiceModel(),
                        true,
                        selection.lossyMappingNotes()))
                .orElseGet(SelectionView::awaiting);
    }

    private Set<ProviderModule> modules(ProviderModule... modules) {
        return Set.of(modules);
    }

    private record SelectionView(
            String providerKey,
            String choiceModel,
            boolean selectedByAdmin,
            List<String> lossyMappingNotes) {
        private static SelectionView awaiting() {
            return new SelectionView("awaiting_admin_selection", "not_selected", false, List.of());
        }

        private SelectionView {
            lossyMappingNotes = lossyMappingNotes == null ? List.of() : List.copyOf(lossyMappingNotes);
        }
    }
}
