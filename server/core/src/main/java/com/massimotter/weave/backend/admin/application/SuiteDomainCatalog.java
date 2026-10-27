package com.massimotter.weave.backend.admin.application;

import com.massimotter.weave.backend.admin.domain.SuiteDomainDeclaration;
import com.massimotter.weave.backend.admin.domain.SuiteDomainDefinition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;

/** Grouped readiness is derived only from explicitly installed owner fragments. */
public final class SuiteDomainCatalog {
    private final List<SuiteDomainDeclaration> groups;

    public SuiteDomainCatalog(List<SuiteDomainDeclaration> declarations) {
        var grouped = new LinkedHashMap<String, List<SuiteDomainDeclaration>>();
        var owners = new LinkedHashSet<String>();
        for (var value : declarations.stream().sorted(Comparator.comparingInt(SuiteDomainDeclaration::order)
                .thenComparingInt(SuiteDomainDeclaration::fragmentOrder)).toList()) {
            if (!owners.add(value.owner() + ":" + value.definition().domain())) {
                throw new IllegalArgumentException("Duplicate Admin suite ownership");
            }
            grouped.computeIfAbsent(value.definition().domain(), ignored -> new ArrayList<>()).add(value);
        }
        var resolved = new ArrayList<SuiteDomainDeclaration>();
        for (var fragments : grouped.values()) {
            var first = fragments.getFirst();
            if (fragments.stream().anyMatch(value -> value.order() != first.order()
                    || !value.definition().label().equals(first.definition().label()))) {
                throw new IllegalArgumentException("Conflicting Admin suite metadata");
            }
            var definition = new SuiteDomainDefinition(first.definition().domain(), first.definition().label(),
                    merge(fragments, SuiteDomainDefinition::providerCategoryKeys),
                    merge(fragments, SuiteDomainDefinition::readCapabilities),
                    merge(fragments, SuiteDomainDefinition::writeCapabilities),
                    merge(fragments, SuiteDomainDefinition::canonicalObjectKinds));
            resolved.add(new SuiteDomainDeclaration(first.owner(), first.order(), first.fragmentOrder(), definition,
                    first.sourceOfTruth(), first.portabilityNotes(), first.nextAction()));
        }
        this.groups = List.copyOf(resolved);
    }

    public List<SuiteDomainDeclaration> groups() {
        return groups;
    }

    private static List<String> merge(List<SuiteDomainDeclaration> fragments,
            Function<SuiteDomainDefinition, List<String>> field) {
        return fragments.stream().flatMap(value -> field.apply(value.definition()).stream()).distinct().toList();
    }
}
