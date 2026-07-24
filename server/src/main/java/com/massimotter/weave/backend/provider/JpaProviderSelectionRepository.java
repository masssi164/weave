package com.massimotter.weave.backend.provider;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter-private ORM projection of the provider-selection aggregate.
 *
 * <p>The aggregate is loaded with its ordered notes through an explicit entity graph. Updating
 * it uses Hibernate dirty checking and optimistic locking; no delete/reinsert replacement is
 * permitted.
 */
@Repository
@Transactional(readOnly = true)
public class JpaProviderSelectionRepository implements ProviderSelectionRepository {

    private final ProviderSelectionJpaRepository selections;

    public JpaProviderSelectionRepository(ProviderSelectionJpaRepository selections) {
        this.selections = requireNonNull(selections, "selections");
    }

    @Override
    public Optional<ProviderSelection> findByCategory(String category) {
        if (category == null || category.isBlank()) {
            return Optional.empty();
        }
        return selections.findAggregateByCategory(normalizeCategory(category))
                .map(ProviderSelectionJpaEntity::toDomain);
    }

    @Override
    public List<ProviderSelection> findAll() {
        return selections.findAllAggregates().stream()
                .map(ProviderSelectionJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public ProviderSelection save(ProviderSelection selection) {
        ProviderSelection normalized = normalizeSelection(
                requireNonNull(selection, "Provider selection must not be null."));
        ProviderSelectionJpaEntity entity = selections.findAggregateByCategory(normalized.category())
                .orElseGet(() -> ProviderSelectionJpaEntity.create(normalized));
        entity.replaceWith(normalized);
        return selections.saveAndFlush(entity).toDomain();
    }

    @Override
    public String persistencePosture() {
        return "durable-relational-jpa-flyway";
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
