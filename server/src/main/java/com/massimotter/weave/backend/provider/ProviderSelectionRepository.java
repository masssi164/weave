package com.massimotter.weave.backend.provider;

import java.util.List;
import java.util.Optional;

public interface ProviderSelectionRepository {
    Optional<ProviderSelection> findByCategory(String category);

    List<ProviderSelection> findAll();

    ProviderSelection save(ProviderSelection selection);
}
