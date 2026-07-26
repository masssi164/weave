package com.massimotter.weave.backend.provider;

import com.massimotter.weave.backend.persistence.jpa.provider.ProviderSelectionEntity;
import com.massimotter.weave.backend.persistence.jpa.provider.ProviderSelectionJpaRepository;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

public class JpaProviderSelectionRepository implements ProviderSelectionRepository {

    private final ProviderSelectionJpaRepository repository;

    public JpaProviderSelectionRepository(ProviderSelectionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProviderSelection> findByCategory(String category) {
        if (category == null || category.isBlank()) {
            return Optional.empty();
        }
        return repository.findById(normalizeCategory(category)).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProviderSelection> findAll() {
        return repository.findAllByOrderByCategoryAsc().stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public ProviderSelection save(ProviderSelection selection) {
        if (selection == null) {
            throw new IllegalArgumentException("Provider selection must not be null.");
        }
        ProviderSelection normalized = normalizeSelection(selection);
        repository.saveAndFlush(toEntity(normalized));
        return normalized;
    }

    @Override
    public String persistencePosture() {
        return "portable-jpa-hibernate-validated";
    }

    private ProviderSelection toDomain(ProviderSelectionEntity entity) {
        return new ProviderSelection(
                entity.category(),
                entity.providerKey(),
                entity.choiceModel(),
                entity.secretRef(),
                entity.selectedBy(),
                entity.selectedAt(),
                entity.applied(),
                entity.supportSafe(),
                entity.migrationDryRunRequired(),
                entity.lossyMappingNotes());
    }

    private ProviderSelectionEntity toEntity(ProviderSelection selection) {
        return new ProviderSelectionEntity(
                selection.category(),
                selection.providerKey(),
                selection.choiceModel(),
                selection.secretRef(),
                selection.selectedBy(),
                selection.selectedAt(),
                selection.applied(),
                selection.supportSafe(),
                selection.migrationDryRunRequired(),
                selection.lossyMappingNotes());
    }

    private ProviderSelection normalizeSelection(ProviderSelection selection) {
        return new ProviderSelection(
                normalizeCategory(selection.category()),
                selection.providerKey(),
                selection.choiceModel(),
                selection.secretRef(),
                selection.selectedBy(),
                selection.selectedAt(),
                selection.applied(),
                selection.supportSafe(),
                selection.migrationDryRunRequired(),
                selection.lossyMappingNotes());
    }

    private String normalizeCategory(String category) {
        return category.trim().toLowerCase(Locale.ROOT);
    }
}
