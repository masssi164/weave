package com.massimotter.weave.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import static java.util.Objects.requireNonNull;

@Repository
@Transactional(readOnly = true)
public class JpaProductProfileOverrideRepository implements ProductProfileOverrideRepository {

    private static final TypeReference<Map<String, String>> ACCESSIBILITY_PREFERENCES =
            new TypeReference<>() {
            };

    private final ProductProfileOverrideJpaRepository profiles;
    private final ObjectMapper objectMapper;

    public JpaProductProfileOverrideRepository(
            ProductProfileOverrideJpaRepository profiles,
            ObjectMapper objectMapper) {
        this.profiles = requireNonNull(profiles, "profiles");
        this.objectMapper = requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public ProductProfileOverride findByPrimaryIdentityKey(String primaryIdentityKey) {
        if (primaryIdentityKey == null || primaryIdentityKey.isBlank()) {
            return null;
        }
        return profiles.findById(primaryIdentityKey)
                .map(this::toDomain)
                .orElse(null);
    }

    @Override
    @Transactional
    public ProductProfileOverride saveForPrimaryIdentityKey(
            String primaryIdentityKey,
            ProductProfileOverride profile) {
        if (primaryIdentityKey == null || primaryIdentityKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Product profile override key must be a non-blank primary identity key.");
        }
        if (profile == null) {
            throw new IllegalArgumentException("Product profile override must not be null.");
        }
        ProductProfileOverrideJpaEntity entity = profiles.findById(primaryIdentityKey)
                .orElseGet(() -> new ProductProfileOverrideJpaEntity(primaryIdentityKey));
        entity.replaceWith(
                profile.displayName(),
                profile.avatar(),
                profile.locale(),
                profile.timezone(),
                accessibilityPreferencesJson(profile.accessibilityPreferences()),
                profile.profileVisibility());
        return toDomain(profiles.saveAndFlush(entity));
    }

    public String persistencePosture() {
        return "durable-relational-jpa-flyway";
    }

    private ProductProfileOverride toDomain(ProductProfileOverrideJpaEntity entity) {
        return new ProductProfileOverride(
                entity.displayName(),
                entity.avatar(),
                entity.locale(),
                entity.timezone(),
                accessibilityPreferences(entity.accessibilityPreferencesJson()),
                entity.profileVisibility());
    }

    private String accessibilityPreferencesJson(Map<String, String> preferences) {
        try {
            return objectMapper.writeValueAsString(preferences == null ? Map.of() : preferences);
        } catch (JsonProcessingException exception) {
            throw new ProductProfileStoreException(
                    "Failed to serialize product profile accessibility preferences.",
                    exception);
        }
    }

    private Map<String, String> accessibilityPreferences(String preferencesJson) {
        if (preferencesJson == null || preferencesJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(preferencesJson, ACCESSIBILITY_PREFERENCES);
        } catch (JsonProcessingException exception) {
            throw new ProductProfileStoreException(
                    "Failed to load product profile accessibility preferences.",
                    exception);
        }
    }
}
