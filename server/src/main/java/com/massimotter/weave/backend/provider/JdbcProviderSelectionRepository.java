package com.massimotter.weave.backend.provider;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public class JdbcProviderSelectionRepository implements ProviderSelectionRepository {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public JdbcProviderSelectionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(jdbcTemplate.getDataSource()));
    }

    @Override
    public Optional<ProviderSelection> findByCategory(String category) {
        if (category == null || category.isBlank()) {
            return Optional.empty();
        }
        List<ProviderSelection> selections = jdbcTemplate.query(
                "select category, provider_key, choice_model, secret_ref, selected_by, selected_at_utc, applied, "
                        + "support_safe, migration_dry_run_required "
                        + "from weave_provider_selections where category = ?",
                (rs, rowNum) -> mapSelection(rs),
                normalizeCategory(category));
        return selections.stream().findFirst();
    }

    @Override
    public List<ProviderSelection> findAll() {
        return jdbcTemplate.query(
                "select category, provider_key, choice_model, secret_ref, selected_by, selected_at_utc, applied, "
                        + "support_safe, migration_dry_run_required "
                        + "from weave_provider_selections order by category",
                (rs, rowNum) -> mapSelection(rs));
    }

    @Override
    public ProviderSelection save(ProviderSelection selection) {
        if (selection == null) {
            throw new IllegalArgumentException("Provider selection must not be null.");
        }
        ProviderSelection normalizedSelection = normalizeSelection(selection);
        return transactionTemplate.execute(status -> {
            jdbcTemplate.update("delete from weave_provider_selection_notes where category = ?", normalizedSelection.category());
            jdbcTemplate.update("delete from weave_provider_selections where category = ?", normalizedSelection.category());
            jdbcTemplate.update(
                    "insert into weave_provider_selections "
                            + "(category, provider_key, choice_model, secret_ref, selected_by, selected_at_utc, "
                            + "applied, support_safe, migration_dry_run_required) "
                            + "values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    normalizedSelection.category(),
                    normalizedSelection.providerKey(),
                    normalizedSelection.choiceModel(),
                    normalizedSelection.secretRef(),
                    normalizedSelection.selectedBy(),
                    normalizedSelection.selectedAt().toString(),
                    normalizedSelection.applied(),
                    normalizedSelection.supportSafe(),
                    normalizedSelection.migrationDryRunRequired());
            List<String> notes = normalizedSelection.lossyMappingNotes();
            for (int index = 0; index < notes.size(); index++) {
                jdbcTemplate.update(
                        "insert into weave_provider_selection_notes (category, note_order, note_text) values (?, ?, ?)",
                        normalizedSelection.category(),
                        index,
                        notes.get(index));
            }
            return normalizedSelection;
        });
    }

    @Override
    public String persistencePosture() {
        return "durable-relational-flyway";
    }

    private ProviderSelection mapSelection(ResultSet rs) throws SQLException {
        String category = rs.getString("category");
        return new ProviderSelection(
                category,
                rs.getString("provider_key"),
                rs.getString("choice_model"),
                rs.getString("secret_ref"),
                rs.getString("selected_by"),
                Instant.parse(rs.getString("selected_at_utc")),
                rs.getBoolean("applied"),
                rs.getBoolean("support_safe"),
                rs.getBoolean("migration_dry_run_required"),
                notes(category));
    }

    private List<String> notes(String category) {
        return jdbcTemplate.query(
                "select note_text from weave_provider_selection_notes where category = ? order by note_order",
                (rs, rowNum) -> rs.getString("note_text"),
                category);
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
