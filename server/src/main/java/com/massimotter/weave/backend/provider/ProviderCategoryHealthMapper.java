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
            WorkspaceCapabilitiesResponse capabilities) {
        List<ProviderStatusResponse> safeProviders = providers == null ? List.of() : List.copyOf(providers);
        return List.of(
                capabilityCategory(
                        "identity-idm",
                        "identity/IDM",
                        capabilities.shellAccess(),
                        safeProviders,
                        modules(ProviderModule.IDENTITY_REALM, ProviderModule.MATRIX_AUTH)),
                capabilityCategory(
                        "chat",
                        "chat",
                        capabilities.chat(),
                        safeProviders,
                        modules(ProviderModule.MATRIX)),
                capabilityCategory(
                        "files",
                        "files",
                        capabilities.files(),
                        safeProviders,
                        modules(ProviderModule.FILES)),
                capabilityCategory(
                        "calendar",
                        "calendar",
                        capabilities.calendar(),
                        safeProviders,
                        modules(ProviderModule.CALENDAR)),
                capabilityCategory(
                        "boards-tasks",
                        "boards/tasks",
                        capabilities.boards(),
                        safeProviders,
                        modules(ProviderModule.BOARDS)),
                providerCategory(
                        "meetings-calls",
                        "meetings/calls",
                        "Meetings and calls are available only when the configured media provider is ready behind the backend token facade.",
                        safeProviders,
                        modules(ProviderModule.MEETINGS)),
                providerCategory(
                        "documents-collaboration",
                        "documents/collaboration",
                        "Document collaboration is available only through backend-owned launch/capability facades.",
                        safeProviders,
                        modules(ProviderModule.OFFICE, ProviderModule.FORMS, ProviderModule.CONTACTS)),
                capabilityCategory(
                        "decisions-evidence",
                        "decisions/evidence",
                        capabilities.decisionsEvidence(),
                        safeProviders,
                        Set.of()),
                capabilityCategory(
                        "manuals-help",
                        "manuals/help",
                        capabilities.manualsHelp(),
                        safeProviders,
                        Set.of()),
                capabilityCategory(
                        "release-evidence",
                        "release evidence",
                        capabilities.releaseEvidence(),
                        safeProviders,
                        modules(ProviderModule.RELEASE)),
                capabilityCategory(
                        "admin-control-plane",
                        "admin control plane",
                        capabilities.adminControlPlane(),
                        safeProviders,
                        Set.of()),
                capabilityCategory(
                        "weaver",
                        "Weaver",
                        capabilities.weaver(),
                        safeProviders,
                        Set.of()));
    }

    private static ProviderCategoryStatusResponse capabilityCategory(
            String category,
            String label,
            WorkspaceCapabilityStatusResponse capability,
            List<ProviderStatusResponse> providers,
            Set<ProviderModule> modules) {
        return new ProviderCategoryStatusResponse(
                category,
                label,
                ProviderCapabilityContracts.contract(category, modules),
                fromCapability(capability),
                capability.policyState(),
                capability.memberImpact(),
                moduleNames(modules),
                providerCandidates(providers, modules),
                diagnostics(providers, modules, Map.of(
                        "capabilityEnabled", capability.enabled(),
                        "capabilityReadiness", capability.readiness().value(),
                        "effectivePolicyState", capability.policyState().value(),
                        "grantedCapabilityCount", capability.grantedCapabilities().size(),
                        "diagnosticsRedacted", true)));
    }

    private static ProviderCategoryStatusResponse providerCategory(
            String category,
            String label,
            String memberImpact,
            List<ProviderStatusResponse> providers,
            Set<ProviderModule> modules) {
        List<ProviderStatusResponse> matching = matching(providers, modules);
        ProviderCategoryReadiness readiness = fromProviders(matching);
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
                memberImpact,
                moduleNames(modules),
                providerCandidates(providers, modules),
                diagnostics(providers, modules, Map.of(
                        "effectivePolicyState", policyState.value(),
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

    private static List<String> providerCandidates(List<ProviderStatusResponse> providers, Set<ProviderModule> modules) {
        return matching(providers, modules).stream()
                .flatMap(provider -> {
                    List<String> values = new ArrayList<>();
                    values.add(provider.providerKey());
                    values.addAll(provider.candidates());
                    return values.stream();
                })
                .distinct()
                .sorted()
                .toList();
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

    private static Set<ProviderModule> modules(ProviderModule... modules) {
        return Set.of(modules);
    }
}
