package com.massimotter.weave.backend.provider;

import com.massimotter.weave.backend.model.WorkspaceCapabilityPolicyState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class DomainAdapterRegistryMapper {

    private DomainAdapterRegistryMapper() {
    }

    static DomainAdapterRegistryResponse fromCategories(List<ProviderCategoryStatusResponse> categories, Instant generatedAt) {
        List<DomainAdapterStatusResponse> domains = (categories == null ? List.<ProviderCategoryStatusResponse>of() : categories).stream()
                .flatMap(category -> CanonicalDomainBindingCatalog.domainsForCategory(category.category()).stream()
                        .map(domain -> fromCategory(category, domain.key(), domain.displayName())))
                .toList();
        return new DomainAdapterRegistryResponse(
                "domain-adapter-registry-preview",
                true,
                false,
                true,
                generatedAt,
                domains);
    }

    static DomainAdapterStatusResponse fromCategory(ProviderCategoryStatusResponse category) {
        return CanonicalDomainBindingCatalog.primaryDomainForCategory(category.category())
                .map(domain -> fromCategory(category, domain.key(), domain.displayName()))
                .orElseGet(() -> fromCategory(category, category.category(), category.label()));
    }

    private static DomainAdapterStatusResponse fromCategory(ProviderCategoryStatusResponse category, String domainKey, String label) {
        boolean enabled = category.policyState() != WorkspaceCapabilityPolicyState.DISABLED
                && category.readiness() != ProviderCategoryReadiness.DISABLED;
        List<String> adapterKeys = new ArrayList<>();
        adapterKeys.addAll(category.contract().defaultAdapters());
        adapterKeys.addAll(category.contract().externalAdapters());
        adapterKeys = adapterKeys.stream().distinct().toList();
        String selectedAdapter = category.selectedByAdmin() && adapterKeys.contains(category.selectedProviderKey())
                ? category.selectedProviderKey()
                : null;
        String activeAdapter = enabled
                ? selectedAdapter != null
                        ? selectedAdapter
                        : category.contract().defaultAdapters().isEmpty()
                                ? null
                                : category.contract().defaultAdapters().get(0)
                : null;
        List<DomainAdapterCandidateResponse> candidates = adapterKeys.stream()
                .map(adapter -> candidate(category, adapter, adapter.equals(activeAdapter)))
                .toList();
        long activeCount = candidates.stream().filter(DomainAdapterCandidateResponse::active).count();
        List<String> violations = new ArrayList<>();
        boolean failClosed = false;
        if (enabled && activeCount != 1) {
            violations.add("enabled domain must have exactly one active adapter; selection fails closed until admin fixes it");
            failClosed = true;
        } else if (!enabled && activeCount > 0) {
            violations.add("disabled domain must not expose an active adapter");
            failClosed = true;
        }
        return new DomainAdapterStatusResponse(
                domainKey,
                label,
                enabled,
                activeCount == 1 ? activeAdapter : null,
                failClosed ? ProviderCategoryReadiness.MISCONFIGURED : category.readiness(),
                failClosed ? "This domain is fail-closed until an admin selects exactly one active adapter." : category.memberImpact(),
                candidates,
                violations,
                failClosed || category.diagnostics().getOrDefault("allFailClosed", true).equals(Boolean.TRUE),
                true);
    }

    private static DomainAdapterCandidateResponse candidate(
            ProviderCategoryStatusResponse category,
            String adapterKey,
            boolean active) {
        String choiceModel = category.contract().defaultAdapters().contains(adapterKey)
                ? "recommended_self_hosted_default"
                : "external_or_managed_candidate";
        return new DomainAdapterCandidateResponse(
                adapterKey,
                choiceModel,
                active,
                active && (category.readiness() == ProviderCategoryReadiness.READY
                        || category.readiness() == ProviderCategoryReadiness.DEGRADED),
                active ? category.readiness() : ProviderCategoryReadiness.DISABLED,
                active ? category.providerRealityLevel() : ProviderRealityLevel.CONTRACT_ONLY,
                active ? category.realityLevelRemediation() : "Candidate is inactive until admin selection and evidence promotion.",
                List.of("dry-run", "support-safe-evidence", "admin-review-required"),
                choiceModel.equals("recommended_self_hosted_default")
                        ? List.of("self-hosted default; admin still validates backup, update, jurisdiction, and support posture")
                        : List.of("candidate adapter; admin must validate scopes, export/import fidelity, residency, rate limits, and rollback evidence"),
                true,
                Map.of(
                        "providerRealityLevel", (active ? category.providerRealityLevel() : ProviderRealityLevel.CONTRACT_ONLY).value(),
                        "diagnosticsRedacted", true,
                        "secretsReturned", false,
                        "rawProviderErrorsReturned", false));
    }
}
