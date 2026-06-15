package com.massimotter.weave.backend.provider;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryProviderSelectionRepository implements ProviderSelectionRepository {

    private final ConcurrentHashMap<String, ProviderSelection> selections = new ConcurrentHashMap<>();

    @Override
    public Optional<ProviderSelection> findByCategory(String category) {
        if (category == null || category.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(selections.get(ProviderSelectionKey.category(category)));
    }

    @Override
    public List<ProviderSelection> findAll() {
        return selections.values().stream()
                .sorted(Comparator.comparing(ProviderSelection::category))
                .toList();
    }

    @Override
    public ProviderSelection save(ProviderSelection selection) {
        if (selection == null) {
            throw new IllegalArgumentException("Provider selection must not be null.");
        }
        selections.put(ProviderSelectionKey.category(selection.category()), selection);
        return selection;
    }

    public void clear() {
        selections.clear();
    }
}
