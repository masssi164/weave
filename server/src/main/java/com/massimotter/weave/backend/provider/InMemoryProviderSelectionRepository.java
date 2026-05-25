package com.massimotter.weave.backend.provider;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryProviderSelectionRepository implements ProviderSelectionRepository {

    private final ConcurrentHashMap<String, ProviderSelection> selections = new ConcurrentHashMap<>();

    @Override
    public Optional<ProviderSelection> findByCategory(String category) {
        if (category == null || category.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(selections.get(category.trim()));
    }

    @Override
    public List<ProviderSelection> findAll() {
        return selections.values().stream()
                .sorted(Comparator.comparing(ProviderSelection::category))
                .toList();
    }

    @Override
    public ProviderSelection save(ProviderSelection selection) {
        selections.put(selection.category(), selection);
        return selection;
    }

    public void clear() {
        selections.clear();
    }
}
