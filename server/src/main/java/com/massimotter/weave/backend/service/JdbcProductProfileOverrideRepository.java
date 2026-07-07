package com.massimotter.weave.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public class JdbcProductProfileOverrideRepository implements ProductProfileOverrideRepository {

    private static final TypeReference<Map<String, String>> ACCESSIBILITY_PREFERENCES = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public JdbcProductProfileOverrideRepository(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, new ObjectMapper().findAndRegisterModules());
    }

    JdbcProductProfileOverrideRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        DataSource dataSource = jdbcTemplate.getDataSource();
        if (dataSource == null) {
            throw new IllegalArgumentException(
                    "JdbcProductProfileOverrideRepository requires a JdbcTemplate with a DataSource.");
        }
        this.transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Override
    public ProductProfileOverride findByPrimaryIdentityKey(String primaryIdentityKey) {
        if (primaryIdentityKey == null || primaryIdentityKey.isBlank()) {
            return null;
        }
        return jdbcTemplate.query(
                        "select display_name, avatar, locale, timezone, accessibility_preferences_json, "
                                + "profile_visibility from weave_product_profile_overrides where primary_identity_key = ?",
                        (rs, rowNum) -> mapProfile(rs),
                        primaryIdentityKey)
                .stream()
                .findFirst()
                .orElse(null);
    }

    @Override
    public ProductProfileOverride saveForPrimaryIdentityKey(String primaryIdentityKey, ProductProfileOverride profile) {
        if (primaryIdentityKey == null || primaryIdentityKey.isBlank()) {
            throw new IllegalArgumentException("Product profile override key must be a non-blank primary identity key.");
        }
        if (profile == null) {
            throw new IllegalArgumentException("Product profile override must not be null.");
        }
        return transactionTemplate.execute(status -> {
            jdbcTemplate.update(
                    "delete from weave_product_profile_overrides where primary_identity_key = ?",
                    primaryIdentityKey);
            jdbcTemplate.update(
                    "insert into weave_product_profile_overrides "
                            + "(primary_identity_key, display_name, avatar, locale, timezone, "
                            + "accessibility_preferences_json, profile_visibility) "
                            + "values (?, ?, ?, ?, ?, ?, ?)",
                    primaryIdentityKey,
                    profile.displayName(),
                    profile.avatar(),
                    profile.locale(),
                    profile.timezone(),
                    accessibilityPreferencesJson(profile.accessibilityPreferences()),
                    profile.profileVisibility());
            return profile;
        });
    }

    public String persistencePosture() {
        return "durable-relational-flyway";
    }

    private ProductProfileOverride mapProfile(ResultSet rs) throws SQLException {
        return new ProductProfileOverride(
                rs.getString("display_name"),
                rs.getString("avatar"),
                rs.getString("locale"),
                rs.getString("timezone"),
                accessibilityPreferences(rs.getString("accessibility_preferences_json")),
                rs.getString("profile_visibility"));
    }

    private String accessibilityPreferencesJson(Map<String, String> preferences) {
        try {
            return objectMapper.writeValueAsString(preferences == null ? Map.of() : preferences);
        } catch (JsonProcessingException exception) {
            throw new ProductProfileStoreException("Failed to serialize product profile accessibility preferences.", exception);
        }
    }

    private Map<String, String> accessibilityPreferences(String preferencesJson) {
        if (preferencesJson == null || preferencesJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(preferencesJson, ACCESSIBILITY_PREFERENCES);
        } catch (JsonProcessingException exception) {
            throw new ProductProfileStoreException("Failed to load product profile accessibility preferences.", exception);
        }
    }
}
