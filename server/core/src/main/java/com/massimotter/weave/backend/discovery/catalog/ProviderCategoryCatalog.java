package com.massimotter.weave.backend.discovery.catalog;

import com.massimotter.weave.backend.discovery.provider.ProviderModule;
import com.massimotter.weave.backend.discovery.provider.ProviderRealityLevel;
import com.massimotter.weave.backend.discovery.provider.ProviderStatusResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** Validates installed declarations and resolves module membership without a global feature catalog. */
public final class ProviderCategoryCatalog {
    private final List<Category> categories;
    private final Map<String, Category> byKey;
    private final Map<ProviderModule, String> byModule;

    public ProviderCategoryCatalog(List<ProviderCategoryFragment> fragments) {
        Map<String, List<ProviderCategoryFragment>> grouped = new LinkedHashMap<>();
        Set<String> owners = new LinkedHashSet<>();
        for (var fragment : fragments.stream().sorted(Comparator.comparingInt(ProviderCategoryFragment::order)
                .thenComparing(ProviderCategoryFragment::key).thenComparing(ProviderCategoryFragment::owner)).toList()) {
            if (!owners.add(fragment.owner() + ":" + fragment.key())) {
                throw new IllegalArgumentException("Duplicate category fragment: " + fragment.owner() + ":" + fragment.key());
            }
            grouped.computeIfAbsent(fragment.key(), ignored -> new ArrayList<>()).add(fragment);
        }
        List<Category> resolved = new ArrayList<>();
        Map<ProviderModule, String> moduleOwners = new LinkedHashMap<>();
        for (var entries : grouped.values()) {
            var first = entries.getFirst();
            Set<ProviderModule> modules = new LinkedHashSet<>();
            for (var entry : entries) {
                if (first.order() != entry.order() || !first.label().equals(entry.label())
                        || !Objects.equals(first.capabilityDomain(), entry.capabilityDomain())
                        || first.selectable() != entry.selectable() || first.defaultReality() != entry.defaultReality()
                        || !Objects.equals(first.memberImpact(), entry.memberImpact())
                        || !first.contract().sourceOfTruth().equals(entry.contract().sourceOfTruth())
                        || !first.contract().exportDeleteExpectation().equals(entry.contract().exportDeleteExpectation())
                        || !first.contract().replacementRequirement().equals(entry.contract().replacementRequirement())) {
                    throw new IllegalArgumentException("Conflicting category policy: " + first.key());
                }
                for (var module : entry.modules()) {
                    if (!modules.add(module) || moduleOwners.putIfAbsent(module, entry.key()) != null) {
                        throw new IllegalArgumentException("Duplicate category module ownership: " + module);
                    }
                }
            }
            var contract = new ProviderCategoryContract(
                    merge(entries, ProviderCategoryContract::featureCapabilities),
                    merge(entries, ProviderCategoryContract::defaultAdapters),
                    merge(entries, ProviderCategoryContract::externalAdapters),
                    merge(entries, ProviderCategoryContract::canonicalObjects), first.contract().sourceOfTruth(),
                    merge(entries, ProviderCategoryContract::lossyMappingRisks),
                    first.contract().exportDeleteExpectation(), first.contract().replacementRequirement());
            resolved.add(new Category(first.key(), first.label(), Set.copyOf(modules), first.capabilityDomain(),
                    first.selectable(), first.order(), first.memberImpact(), first.defaultReality(), contract));
        }
        this.categories = List.copyOf(resolved);
        Map<String, Category> indexed = new LinkedHashMap<>();
        for (var category : categories) indexed.put(category.key(), category);
        this.byKey = Map.copyOf(indexed);
        this.byModule = Map.copyOf(moduleOwners);
    }

    public List<Category> categories() {
        return categories;
    }

    /** Only categories that the existing administrative selection API supports. */
    public List<String> categoryKeys() {
        return categories.stream().filter(Category::selectable).map(Category::key).toList();
    }

    public Optional<Category> category(String key) {
        return Optional.ofNullable(byKey.get(key)).filter(Category::selectable);
    }

    public Category statusCategory(String key) {
        Category category = byKey.get(key);
        if (category == null) throw new IllegalArgumentException("Provider category is not installed: " + key);
        return category;
    }

    public Optional<String> categoryForModule(ProviderModule module) {
        return Optional.ofNullable(byModule.get(module));
    }

    public boolean providerMatchesCategory(ProviderStatusResponse provider, String categoryKey) {
        return category(categoryKey).map(value -> value.modules().contains(provider.module())).orElse(false);
    }

    private static List<String> merge(List<ProviderCategoryFragment> entries,
            Function<ProviderCategoryContract, List<String>> field) {
        return entries.stream().flatMap(entry -> field.apply(entry.contract()).stream()).distinct().toList();
    }

    public record Category(String key, String label, Set<ProviderModule> modules, String capabilityDomain,
            boolean selectable, int order, String memberImpact, ProviderRealityLevel defaultReality,
            ProviderCategoryContract contract) {
        public boolean capabilityBacked() {
            return capabilityDomain != null;
        }
    }
}
