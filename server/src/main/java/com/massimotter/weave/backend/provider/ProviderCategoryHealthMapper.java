package com.massimotter.weave.backend.provider;

import com.massimotter.weave.backend.model.WorkspaceCapabilitiesResponse;
import com.massimotter.weave.backend.model.WorkspaceCapabilityPolicyState;
import com.massimotter.weave.backend.model.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.model.WorkspaceCapabilityStatusResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

final class ProviderCategoryHealthMapper {

    private ProviderCategoryHealthMapper() {
    }

    static List<ProviderCategoryStatusResponse> categories(
            List<ProviderStatusResponse> providers,
            WorkspaceCapabilitiesResponse capabilities,
            ProviderSelectionRepository selections) {
        List<ProviderStatusResponse> safeProviders = providers == null ? List.of() : List.copyOf(providers);
        return List.of(
                capabilityCategory(
                        "identity-idm",
                        "identity/IDM",
                        capabilities.shellAccess(),
                        safeProviders,
                        modules(ProviderModule.IDENTITY_REALM, ProviderModule.MATRIX_AUTH),
                        selections),
                capabilityCategory(
                        "chat",
                        "chat",
                        capabilities.chat(),
                        safeProviders,
                        modules(ProviderModule.MATRIX),
                        selections),
                capabilityCategory(
                        "files",
                        "files",
                        capabilities.files(),
                        safeProviders,
                        modules(ProviderModule.FILES),
                        selections),
                capabilityCategory(
                        "calendar",
                        "calendar",
                        capabilities.calendar(),
                        safeProviders,
                        modules(ProviderModule.CALENDAR),
                        selections),
                capabilityCategory(
                        "boards-tasks",
                        "boards/tasks",
                        capabilities.boards(),
                        safeProviders,
                        modules(ProviderModule.BOARDS),
                        selections),
                providerCategory(
                        "meetings-calls",
                        "meetings/calls",
                        "Meetings and calls are available only when the configured media provider is ready behind the backend token facade.",
                        safeProviders,
                        modules(ProviderModule.MEETINGS),
                        selections),
                providerCategory(
                        "documents-collaboration",
                        "documents/collaboration",
                        "Document collaboration is available only through backend-owned launch/capability facades.",
                        safeProviders,
                        modules(ProviderModule.OFFICE, ProviderModule.FORMS, ProviderModule.CONTACTS),
                        selections),
                capabilityCategory(
                        "weaver",
                        "Weaver",
                        capabilities.weaver(),
                        safeProviders,
                        Set.of(),
                        selections));
    }

    private static ProviderCategoryStatusResponse capabilityCategory(
            String category,
            String label,
            WorkspaceCapabilityStatusResponse capability,
            List<ProviderStatusResponse> providers,
            Set<ProviderModule> modules,
            ProviderSelectionRepository selections) {
        SelectionView selection = selectionView(category, selections);
        ProviderCategoryReadiness readiness = selection.selectedByAdmin() || modules.isEmpty()
                ? fromCapability(capability)
                : ProviderCategoryReadiness.MISCONFIGURED;
        return new ProviderCategoryStatusResponse(
                category,
                label,
                ProviderCapabilityContracts.contract(category, modules),
                readiness,
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
                diagnostics(providers, modules, Map.of(
                        "capabilityEnabled", capability.enabled(),
                        "capabilityReadiness", capability.readiness().value(),
                        "effectivePolicyState", capability.policyState().value(),
                        "grantedCapabilityCount", capability.grantedCapabilities().size(),
                        "providerConfigSource", ProviderRegistry.PROVIDER_CONFIG_SOURCE,
                        "selectionRequiredBeforeProviderUse", !selection.selectedByAdmin() && !modules.isEmpty(),
                        "diagnosticsRedacted", true)));
    }

    private static ProviderCategoryStatusResponse providerCategory(
            String category,
            String label,
            String memberImpact,
            List<ProviderStatusResponse> providers,
            Set<ProviderModule> modules,
            ProviderSelectionRepository selections) {
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
        return new ProviderCategoryStatusResponse(
                category,
                label,
                ProviderCapabilityContracts.contract(category, modules),
                readiness,
                policyState,
                selection.selectedByAdmin() ? memberImpact : "Admin provider mapping is required before this category becomes product-ready.",
                moduleNames(modules),
                providerCandidates(category, providers, modules),
                selection.providerKey(),
                selection.choiceModel(),
                selection.selectedByAdmin(),
                !selection.selectedByAdmin(),
                selection.lossyMappingNotes(),
                diagnostics(providers, modules, Map.of(
                        "effectivePolicyState", policyState.value(),
                        "providerConfigSource", ProviderRegistry.PROVIDER_CONFIG_SOURCE,
                        "selectionRequiredBeforeProviderUse", !selection.selectedByAdmin(),
                        "diagnosticsRedacted", true)));
    }

    private static ProviderCategoryReadiness fromCapability(WorkspaceCapabilityStatusResponse capability) {
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

    private static ProviderCategoryReadiness fromProviders(List<ProviderStatusResponse> providers) {
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

    private static Map<String, Object> diagnostics(
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
        diagnostics.put("secretsReturned", false);
        diagnostics.put("rawProviderErrorsReturned", false);
        diagnostics.putAll(extra);
        return diagnostics;
    }

    private static List<String> providerCandidates(String category, List<ProviderStatusResponse> providers, Set<ProviderModule> modules) {
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
        return ProviderCapabilityContracts.providerCandidates(category);
    }

    private static List<String> moduleNames(Set<ProviderModule> modules) {
        return modules.stream()
                .map(ProviderModule::contractName)
                .sorted()
                .toList();
    }

    private static List<ProviderStatusResponse> matching(List<ProviderStatusResponse> providers, Set<ProviderModule> modules) {
        Predicate<ProviderStatusResponse> predicate = modules.isEmpty()
                ? provider -> false
                : provider -> modules.contains(provider.module());
        return providers.stream()
                .filter(predicate)
                .sorted(Comparator.comparing(provider -> provider.module().contractName()))
                .toList();
    }

    private static SelectionView selectionView(String category, ProviderSelectionRepository selections) {
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

    private static Set<ProviderModule> modules(ProviderModule... modules) {
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
