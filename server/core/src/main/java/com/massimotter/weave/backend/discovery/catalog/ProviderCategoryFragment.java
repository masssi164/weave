package com.massimotter.weave.backend.discovery.catalog;

import com.massimotter.weave.backend.discovery.provider.ProviderModule;
import com.massimotter.weave.backend.discovery.provider.ProviderRealityLevel;
import java.util.Objects;
import java.util.Set;

/** One installed owner's contribution to a provider category; categories may span explicit module fragments. */
public record ProviderCategoryFragment(
        String owner,
        int order,
        String key,
        String label,
        String capabilityDomain,
        boolean selectable,
        String memberImpact,
        ProviderRealityLevel defaultReality,
        Set<ProviderModule> modules,
        ProviderCategorySelectionPolicy selectionPolicy,
        ProviderCategoryContract contract) {
    public ProviderCategoryFragment {
        if (owner == null || owner.isBlank() || key == null || key.isBlank() || label == null || label.isBlank()) {
            throw new IllegalArgumentException("Category owner, key and label are required");
        }
        if (capabilityDomain == null && (memberImpact == null || memberImpact.isBlank())) {
            throw new IllegalArgumentException("Provider-backed categories require member impact metadata");
        }
        defaultReality = Objects.requireNonNull(defaultReality, "defaultReality");
        modules = Set.copyOf(modules);
        selectionPolicy = Objects.requireNonNull(selectionPolicy, "selectionPolicy");
        contract = Objects.requireNonNull(contract, "contract");
    }
}
